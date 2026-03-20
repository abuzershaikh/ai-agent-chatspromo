package com.message.bulksend.aiagent.tools.agentform

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.aiagent.tools.agentform.models.FieldType
import com.message.bulksend.aiagent.tools.agentform.models.FormField
import com.message.bulksend.aiagent.tools.agentform.models.StoredFormConfig
import com.message.bulksend.autorespond.database.MessageRepository
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.CellModel
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FolderModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.models.TableModel
import com.message.bulksend.userdetails.UserDetailsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

class AgentFormTableSheetSyncManager(context: Context) {

    companion object {
        private const val TAG = "AgentFormSheetSync"

        const val AGENTFORM_FOLDER_NAME = "agentform"
        const val CONTACT_VERIFY_SHEET_NAME = "Contact Save Verification"
        private const val TEMPLATE_SHEET_PREFIX = "Contact Verify - "
        private const val MAX_SHEET_NAME = 62
        private const val LEGACY_TEMPLATE_PREFIX = "Template - "
        private const val DELETE_TEMPLATE_API_URL = "https://chataiform.com/api/delete-template-data"
        private const val TEMPLATE_KEY_META_PREFIX = "agentform_template_key="
        private const val TEMPLATE_CONTACT_META_PREFIX = "contact_verify="
        private const val TEMPLATE_GOOGLE_META_PREFIX = "google_auth="
        private const val TEMPLATE_LOCATION_META_PREFIX = "location_verify="

        private const val PREBUILT_ROWS = 120
        private const val EXPAND_ROWS = 25
        private const val MIN_BUFFER_ROWS = 40

        private const val SAVE = "SAVE"
        private const val NOT_SAVE = "NOT_SAVE"
        private const val PENDING = "PENDING"
        private const val YES = "YES"
        private const val NO = "NO"

        private const val COL_VERIFIED_GMAIL = "Verified Gmail"
        private const val COL_GPS_LOCATION = "GPS Location"
        private const val COL_GPS_LAT = "GPS Latitude"
        private const val COL_GPS_LNG = "GPS Longitude"
        private const val COL_GPS_ACC = "GPS Accuracy"

        private val RESERVED_SUBMISSION_KEYS = setOf(
            "uid",
            "phone",
            "target_phone",
            "targetPhone",
            "campaign",
            "form_id",
            "formId",
            "eventType",
            "submissionStatus",
            "verificationStatus",
            "verificationMessage",
            "timestamp",
            "metadata",
            "device_info",
            "google_user",
            "contacts_json",
            "selected_contact_names",
            "selected_contact_numbers",
            "contact_verification_matches",
            "contact_verification_status",
            "contact_verification_message",
            "verification_location",
            "verification_location_lat",
            "verification_location_lng",
            "verification_location_acc"
        ).map { it.lowercase(Locale.ROOT) }.toSet()

        private val PRESET_TEMPLATE_KEYS = mapOf(
            "preset-address-maps-fillup" to "ADDRESS_LOCATION",
            "preset-address-verification" to "ADDRESS_BASIC",
            "preset-contact-save-verification" to "CONTACT_VERIFY",
            "preset-google-signin-email" to "EMAIL_SIGNIN",
            "preset-basic-profile-email" to "BASIC_PROFILE_EMAIL"
        )
    }

    private data class ColumnSpec(
        val name: String,
        val type: String,
        val selectOptions: String? = null
    )

    private data class VerificationState(
        val recordId: String,
        var name: String = "",
        var number: String = "",
        var formOpened: String = NO,
        var contactSaved: String = PENDING,
        var verificationStatus: String = "pending",
        var createdAt: Long = 0L,
        var updatedAt: Long = 0L
    )

    private data class FormFieldColumn(
        val fieldId: String,
        val columnName: String,
        val columnType: String
    )

    private data class SubmissionValues(
        val values: Map<String, String>,
        val columnTypes: Map<String, String>
    )

    private val appContext = context.applicationContext
    private val db = TableSheetDatabase.getDatabase(appContext)
    private val folderDao = db.folderDao()
    private val tableDao = db.tableDao()
    private val columnDao = db.columnDao()
    private val rowDao = db.rowDao()
    private val cellDao = db.cellDao()

    private val appDb = AppDatabase.getInstance(appContext)
    private val contactGroupDao = appDb.contactGroupDao()
    private val messageRepository = MessageRepository(appContext)
    private val firestore = FirebaseFirestore.getInstance()
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    @Volatile
    private var contactNameCache: Map<String, String>? = null

    private val contactVerificationColumns = listOf(
        ColumnSpec("Record ID", ColumnType.STRING),
        ColumnSpec("Name", ColumnType.STRING),
        ColumnSpec("Number", ColumnType.PHONE),
        ColumnSpec("Form Opened", ColumnType.SELECT, "[\"YES\",\"NO\"]"),
        ColumnSpec("Contact Saved", ColumnType.SELECT, "[\"SAVE\",\"NOT_SAVE\",\"PENDING\"]"),
        ColumnSpec("Verification Status", ColumnType.STRING),
        ColumnSpec("Date Time", ColumnType.STRING)
    )

    private val standardTemplateColumns = listOf(
        ColumnSpec("Record ID", ColumnType.STRING),
        ColumnSpec("Name", ColumnType.STRING),
        ColumnSpec("Number", ColumnType.PHONE),
        ColumnSpec("Form Opened", ColumnType.SELECT, "[\"YES\",\"NO\"]"),
        ColumnSpec("Status", ColumnType.STRING),
        ColumnSpec("Date Time", ColumnType.STRING)
    )

    suspend fun initializeAgentFormSheetSystem() = withContext(Dispatchers.IO) {
        try {
            val folder = ensureFolderExists()
            cleanupLegacyCombinedSheet(folder.id)
            ensureAllTemplateSheets(folder.id)
            cleanupDuplicateTemplateSheets(folder.id)
        } catch (e: Exception) {
            Log.e(TAG, "initialize failed: ${e.message}", e)
        }
    }

    suspend fun ensureSheetForTemplateFormId(formIdRaw: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val formId = formIdRaw.trim()
            if (formId.isBlank()) return@withContext false
            val folder = ensureFolderExists()
            val template = resolveTemplateKey("", formId)
            if (!isAiTemplateKey(template)) return@withContext false
            ensureTemplateSheet(folder.id, template, formId)
            cleanupDuplicateTemplateSheets(folder.id)
            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureSheetForTemplateFormId failed: ${e.message}", e)
            false
        }
    }

    suspend fun deleteTemplateSheetDataEverywhere(sheet: TableModel): Int = withContext(Dispatchers.IO) {
        try {
            val template = parseTemplateKeyFromTable(sheet) ?: return@withContext 0
            if (!isAiTemplateKey(template)) return@withContext 0

            val ownerUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().trim()
            val ownerPhone = resolveOwnerPhone(ownerUid)
            if (ownerUid.isBlank() || ownerPhone.isBlank()) return@withContext 0

            val snapshot = firestore.collection("users")
                .document(ownerUid)
                .collection("numbers")
                .document(ownerPhone)
                .collection("responses")
                .get()
                .await()

            var deletedFromFirestore = 0
            snapshot.documents.forEach { doc ->
                val data = doc.data ?: return@forEach
                val formId = readString(data["form_id"]).ifBlank { readString(data["formId"]) }
                val campaign = readString(data["campaign"])
                val docTemplate = resolveTemplateKey(campaign, formId)
                if (docTemplate != template) return@forEach
                runCatching {
                    doc.reference.delete().await()
                    deletedFromFirestore++
                }.onFailure {
                    Log.e(TAG, "Firestore delete failed for template=$template doc=${doc.id}: ${it.message}", it)
                }
            }

            val deletedFromWorker = deleteTemplateDataViaWorker(ownerUid, ownerPhone, template)
            deletedFromFirestore + deletedFromWorker
        } catch (e: Exception) {
            Log.e(TAG, "deleteTemplateSheetDataEverywhere failed: ${e.message}", e)
            0
        }
    }

    suspend fun logFormLinkSent(
        ownerUid: String,
        ownerPhone: String,
        targetPhoneRaw: String,
        formIdRaw: String,
        campaignRaw: String,
        formLinkRaw: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val uid = ownerUid.trim()
            val target = sanitizePhone(targetPhoneRaw)
            if (uid.isBlank() || target.isBlank()) return@withContext false

            if (!isAiCampaign(campaignRaw)) return@withContext false

            val template = resolveTemplateKey(campaignRaw.trim(), formIdRaw.trim())
            val sheet = ensureTemplateSheet(ensureFolderExists().id, template, formIdRaw.trim())

            val recordId = verificationRecordId(uid, template, target)
            val now = System.currentTimeMillis()
            val existing = loadValuesByRecord(sheet, recordId)
            val state = stateFromValues(existing) ?: VerificationState(recordId = recordId)

            state.number = target
            if (state.createdAt <= 0L) state.createdAt = now
            state.updatedAt = max(state.updatedAt, now)
            if (state.formOpened.isBlank()) state.formOpened = NO
            if (state.contactSaved.isBlank()) state.contactSaved = PENDING
            if (state.verificationStatus.isBlank()) state.verificationStatus = "pending"

            if (state.name.isBlank()) {
                state.name = resolveName(emptyMap(), target, mutableMapOf())
            }

            val updated = upsertRecord(sheet, toRowMap(state, isContactVerificationSheet(sheet)))
            cleanupDuplicateRows(sheet)
            updated
        } catch (e: Exception) {
            Log.e(TAG, "logFormLinkSent failed: ${e.message}", e)
            false
        }
    }

    suspend fun syncResponseDocuments(
        ownerUid: String,
        ownerPhone: String,
        documents: List<DocumentSnapshot>
    ): Int = withContext(Dispatchers.IO) {
        if (ownerUid.isBlank() || ownerPhone.isBlank() || documents.isEmpty()) {
            return@withContext 0
        }

        try {
            val folder = ensureFolderExists()
            val nameCache = mutableMapOf<String, String>()
            val sheetCache = linkedMapOf<String, TemplateSheetContext>()
            var updates = 0

            documents.sortedBy { parseTimestamp(it.get("timestamp")) }.forEach { doc ->
                val data = doc.data ?: return@forEach
                val eventType = readString(data["eventType"]).ifBlank { "submission" }.lowercase(Locale.ROOT)
                val formId = readString(data["form_id"]).ifBlank { readString(data["formId"]) }
                val campaign = readString(data["campaign"])
                if (!isAiCampaign(campaign)) return@forEach
                val template = resolveTemplateKey(campaign, formId)
                val context = sheetCache.getOrPut(template) {
                    val profile = resolveTemplateProfile(template, formId)
                    val sheet = ensureTemplateSheet(folder.id, profile)
                    TemplateSheetContext(sheet = sheet, profile = profile)
                }

                if (
                    upsertVerificationFromEvent(
                        sheet = context.sheet,
                        profile = context.profile,
                        ownerUid = ownerUid.trim(),
                        template = template,
                        data = data,
                        eventType = eventType,
                        nameCache = nameCache
                    )
                ) {
                    updates++
                }
            }

            sheetCache.values.forEach { context ->
                updates += cleanupDuplicateRows(context.sheet)
            }
            updates
        } catch (e: Exception) {
            Log.e(TAG, "sync failed: ${e.message}", e)
            0
        }
    }

    private data class TemplateSheetContext(
        val sheet: TableModel,
        val profile: TemplateProfile
    )

    private suspend fun upsertVerificationFromEvent(
        sheet: TableModel,
        profile: TemplateProfile,
        ownerUid: String,
        template: String,
        data: Map<String, Any>,
        eventType: String,
        nameCache: MutableMap<String, String>
    ): Boolean {
        val isContactTemplate = isContactVerificationSheet(sheet)
        val target = sanitizePhone(readString(data["targetPhone"]).ifBlank { readString(data["target_phone"]) })
        if (target.isBlank()) return false

        val recordId = verificationRecordId(ownerUid, template, target)
        val ts = parseTimestamp(data["timestamp"])
        val status = resolveVerificationStatus(data)
        val saved = mapContactSaved(status)
        val name = resolveName(data, target, nameCache)

        val state = stateFromValues(loadValuesByRecord(sheet, recordId)) ?: VerificationState(recordId = recordId)
        state.number = target
        if (name.isNotBlank()) state.name = name
        if (state.createdAt <= 0L) state.createdAt = ts
        state.createdAt = min(state.createdAt, ts)
        state.updatedAt = max(state.updatedAt, ts)
        state.verificationStatus = status.ifBlank { state.verificationStatus }.ifBlank { "pending" }

        if (eventType == "form_open" || eventType == "submission" || eventType == "contact_verification") {
            state.formOpened = YES
        } else if (state.formOpened.isBlank()) {
            state.formOpened = NO
        }

        if (saved != PENDING || state.contactSaved.isBlank()) {
            state.contactSaved = saved
        }

        val rowValues = LinkedHashMap(toRowMap(state, isContactTemplate))
        if (eventType == "submission") {
            val submission = extractSubmissionValues(data, profile)
            if (submission.values.isNotEmpty()) {
                ensureColumnsForValues(sheet, submission.columnTypes)
                rowValues.putAll(submission.values)
            }
        }

        return upsertRecord(sheet, rowValues)
    }

    private fun toRowMap(s: VerificationState, isContactTemplate: Boolean): Map<String, String> {
        val base = linkedMapOf(
            "Record ID" to s.recordId,
            "Name" to s.name,
            "Number" to s.number,
            "Form Opened" to s.formOpened.ifBlank { NO },
            "Date Time" to displayTime(max(s.updatedAt, s.createdAt))
        )

        return if (isContactTemplate) {
            base.apply {
                put("Contact Saved", s.contactSaved.ifBlank { PENDING })
                put("Verification Status", s.verificationStatus.ifBlank { "pending" })
            }
        } else {
            base.apply {
                put("Status", s.verificationStatus.ifBlank { "submitted" })
            }
        }
    }

    private suspend fun ensureColumnsForValues(sheet: TableModel, columnTypes: Map<String, String>) {
        if (columnTypes.isEmpty()) return

        val columns = columnDao.getColumnsByTableIdSync(sheet.id).sortedBy { it.orderIndex }
        val byNameLower = columns.associateBy { it.name.lowercase(Locale.ROOT) }.toMutableMap()
        var nextOrder = columns.size
        var columnCountChanged = false

        columnTypes.forEach { (name, type) ->
            val normalized = name.trim().lowercase(Locale.ROOT)
            if (normalized.isBlank()) return@forEach

            val existing = byNameLower[normalized]
            if (existing == null) {
                val columnId = columnDao.insertColumn(
                    ColumnModel(
                        tableId = sheet.id,
                        name = name.trim(),
                        type = type,
                        orderIndex = nextOrder
                    )
                )
                byNameLower[normalized] = ColumnModel(
                    id = columnId,
                    tableId = sheet.id,
                    name = name.trim(),
                    type = type,
                    orderIndex = nextOrder
                )
                nextOrder++
                columnCountChanged = true
            } else if (existing.type == ColumnType.STRING && existing.type != type) {
                columnDao.updateColumn(existing.copy(type = type))
            }
        }

        if (columnCountChanged) {
            tableDao.updateColumnCount(sheet.id, nextOrder)
        }
    }

    private fun extractSubmissionValues(data: Map<String, Any>, profile: TemplateProfile): SubmissionValues {
        val values = linkedMapOf<String, String>()
        val columnTypes = linkedMapOf<String, String>()
        val usedNames = linkedSetOf<String>()

        profile.fieldColumns.forEach { field ->
            val value = stringifySubmissionValue(data[field.fieldId])
            if (value.isBlank()) return@forEach

            values[field.columnName] = value
            columnTypes[field.columnName] = field.columnType
            usedNames.add(field.columnName.lowercase(Locale.ROOT))
        }

        val googleEmail = extractVerifiedGmail(data)
        if (googleEmail.isNotBlank()) {
            values[COL_VERIFIED_GMAIL] = googleEmail
            columnTypes[COL_VERIFIED_GMAIL] = ColumnType.EMAIL
            usedNames.add(COL_VERIFIED_GMAIL.lowercase(Locale.ROOT))
        }

        val gps = extractGpsLocation(data)
        if (gps.address.isNotBlank()) {
            values[COL_GPS_LOCATION] = gps.address
            columnTypes[COL_GPS_LOCATION] = ColumnType.STRING
            usedNames.add(COL_GPS_LOCATION.lowercase(Locale.ROOT))
        }
        if (gps.lat.isNotBlank()) {
            values[COL_GPS_LAT] = gps.lat
            columnTypes[COL_GPS_LAT] = ColumnType.STRING
            usedNames.add(COL_GPS_LAT.lowercase(Locale.ROOT))
        }
        if (gps.lng.isNotBlank()) {
            values[COL_GPS_LNG] = gps.lng
            columnTypes[COL_GPS_LNG] = ColumnType.STRING
            usedNames.add(COL_GPS_LNG.lowercase(Locale.ROOT))
        }
        if (gps.acc.isNotBlank()) {
            values[COL_GPS_ACC] = gps.acc
            columnTypes[COL_GPS_ACC] = ColumnType.STRING
            usedNames.add(COL_GPS_ACC.lowercase(Locale.ROOT))
        }

        val mappedFieldIds = profile.fieldColumns
            .map { it.fieldId.lowercase(Locale.ROOT) }
            .toSet()

        data.entries.forEach { (key, rawValue) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isBlank()) return@forEach

            val lowerKey = normalizedKey.lowercase(Locale.ROOT)
            if (RESERVED_SUBMISSION_KEYS.contains(lowerKey)) return@forEach
            if (mappedFieldIds.contains(lowerKey)) return@forEach
            if (lowerKey.endsWith("_base64") || lowerKey.endsWith("_mimetype")) return@forEach

            val value = stringifySubmissionValue(rawValue)
            if (value.isBlank()) return@forEach

            val preferred = toDisplayFieldName(normalizedKey)
            val columnName = uniqueColumnName(preferred, usedNames)
            values[columnName] = value
            columnTypes[columnName] = inferColumnType(normalizedKey)
        }

        return SubmissionValues(values = values, columnTypes = columnTypes)
    }

    private data class GpsLocation(
        val address: String = "",
        val lat: String = "",
        val lng: String = "",
        val acc: String = ""
    )

    private fun extractVerifiedGmail(data: Map<String, Any>): String {
        val metadata = readMap(data["metadata"])
        val directMap = readMap(data["google_user"])
        val directJson = parseJsonObject(readString(data["google_user"]))
        val metadataMap = readMap(metadata["google_user"])

        return firstNonBlank(
            readString(directMap["email"]),
            directJson?.optString("email").orEmpty(),
            readString(metadataMap["email"])
        )
    }

    private fun extractGpsLocation(data: Map<String, Any>): GpsLocation {
        val rawLocation = data["verification_location"]
        val locationMap = readMap(rawLocation)
        val locationJson = parseJsonObject(readString(rawLocation))

        val lat = firstNonBlank(
            readString(data["verification_location_lat"]),
            readString(locationMap["lat"]),
            locationJson?.opt("lat")?.toString().orEmpty()
        )
        val lng = firstNonBlank(
            readString(data["verification_location_lng"]),
            readString(locationMap["lng"]),
            locationJson?.opt("lng")?.toString().orEmpty()
        )
        val acc = firstNonBlank(
            readString(data["verification_location_acc"]),
            readString(locationMap["accuracy"]),
            locationJson?.opt("accuracy")?.toString().orEmpty()
        )
        val address = firstNonBlank(
            readString(locationMap["address"]),
            locationJson?.optString("address").orEmpty()
        ).ifBlank {
            if (lat.isNotBlank() && lng.isNotBlank()) "$lat, $lng" else ""
        }

        return GpsLocation(
            address = address,
            lat = lat,
            lng = lng,
            acc = acc
        )
    }

    private fun parseJsonObject(value: String): JSONObject? {
        val raw = value.trim()
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun stringifySubmissionValue(value: Any?): String {
        if (value == null) return ""
        return when (value) {
            is String -> value.trim()
            is Number, is Boolean -> value.toString()
            is Map<*, *>, is List<*> -> gson.toJson(value)
            else -> value.toString().trim()
        }.trim()
    }

    private fun inferColumnType(key: String): String {
        val lower = key.lowercase(Locale.ROOT)
        return when {
            lower.contains("phone") || lower.contains("mobile") || lower.contains("number") -> ColumnType.PHONE
            lower.contains("mail") || lower.contains("email") || lower.contains("gmail") -> ColumnType.EMAIL
            lower.contains("date") -> ColumnType.DATE
            lower.contains("time") -> ColumnType.TIME
            else -> ColumnType.STRING
        }
    }

    private fun toDisplayFieldName(rawKey: String): String {
        val cleaned = rawKey.trim()
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return "Field"

        return cleaned.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                val lower = part.lowercase(Locale.ROOT)
                if (lower.length <= 3 && lower.all { it.isLetter() }) {
                    lower.uppercase(Locale.ROOT)
                } else {
                    lower.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                    }
                }
            }
    }

    private fun uniqueColumnName(preferred: String, usedLowerNames: MutableSet<String>): String {
        val base = preferred.trim().ifBlank { "Field" }.take(60)
        var candidate = base
        var suffix = 2
        while (usedLowerNames.contains(candidate.lowercase(Locale.ROOT))) {
            candidate = "$base ($suffix)"
            suffix++
        }
        usedLowerNames.add(candidate.lowercase(Locale.ROOT))
        return candidate
    }

    private fun stateFromValues(values: Map<String, String>): VerificationState? {
        val id = values["Record ID"].orEmpty().trim()
        if (id.isBlank()) return null

        return VerificationState(recordId = id).apply {
            name = values["Name"].orEmpty()
            number = sanitizePhone(values["Number"])
            formOpened = values["Form Opened"].orEmpty().ifBlank { NO }
            contactSaved = values["Contact Saved"].orEmpty().ifBlank { PENDING }
            verificationStatus = values["Verification Status"]
                .orEmpty()
                .ifBlank { values["Status"].orEmpty() }
                .ifBlank { "pending" }
            updatedAt = parseAnyTime(values["Date Time"])
            createdAt = updatedAt
        }
    }

    private suspend fun ensureFolderExists(): FolderModel {
        val allFolders = folderDao.getAllFolders().first()
        val sameName = allFolders.filter { it.name.equals(AGENTFORM_FOLDER_NAME, ignoreCase = true) }
        var folder = sameName.maxByOrNull { it.updatedAt }

        if (folder == null) {
            val id = folderDao.insertFolder(FolderModel(name = AGENTFORM_FOLDER_NAME, colorHex = "#0F766E"))
            folder = folderDao.getFolderById(id)
            return requireNotNull(folder)
        }

        if (sameName.size > 1) {
            val primary = folder
            sameName.forEach { duplicate ->
                if (duplicate.id == primary.id) return@forEach
                tableDao.getTablesByFolderIdSync(duplicate.id).forEach { table ->
                    tableDao.updateTableFolder(table.id, primary.id)
                }
                folderDao.deleteFolderById(duplicate.id)
            }
        }

        return requireNotNull(folder)
    }

    private suspend fun cleanupLegacyCombinedSheet(folderId: Long) {
        val tables = tableDao.getTablesByFolderIdSync(folderId)
        tables.filter {
            it.name.equals(CONTACT_VERIFY_SHEET_NAME, ignoreCase = true) ||
                it.name.startsWith(LEGACY_TEMPLATE_PREFIX, ignoreCase = true)
        }
            .forEach { legacy ->
                tableDao.deleteTableById(legacy.id)
            }
    }

    private suspend fun ensureAllTemplateSheets(folderId: Long) {
        val forms = appDb.agentFormDao().getAllFormsOnce()
        forms.forEach { form ->
            val formId = form.formId.trim()
            val key = resolveTemplateKey("", formId)
            if (!isAiTemplateKey(key)) return@forEach
            ensureTemplateSheet(folderId, key, formId)
        }
    }

    private suspend fun cleanupDuplicateTemplateSheets(folderId: Long) {
        val allTables = tableDao.getTablesByFolderIdSync(folderId)
        val templateTables = allTables.mapNotNull { table ->
            val key = parseTemplateKeyFromTable(table) ?: return@mapNotNull null
            canonicalTemplateKey(key) to table
        }
        val grouped = templateTables.groupBy({ it.first }, { it.second })
        grouped.values.forEach { same ->
            if (same.size <= 1) return@forEach
            val keep = same.maxByOrNull { it.updatedAt } ?: return@forEach
            same.forEach { candidate ->
                if (candidate.id != keep.id) {
                    tableDao.deleteTableById(candidate.id)
                }
            }
        }
    }

    private data class TemplateProfile(
        val key: String,
        val displayName: String,
        val requiresContactVerification: Boolean,
        val requiresGoogleAuth: Boolean,
        val requiresLocationVerification: Boolean,
        val fieldColumns: List<FormFieldColumn>
    )

    private suspend fun ensureTemplateSheet(folderId: Long, template: String, formIdRaw: String): TableModel {
        val profile = resolveTemplateProfile(template, formIdRaw)
        return ensureTemplateSheet(folderId, profile)
    }

    private suspend fun ensureTemplateSheet(folderId: Long, profile: TemplateProfile): TableModel {
        val specs = buildTemplateColumnSpecs(profile)
        val name = buildTemplateSheetName(profile.displayName)
        val description = buildTemplateDescription(profile)
        return ensureSheet(folderId, name, description, specs)
    }

    private suspend fun resolveTemplateProfile(template: String, formIdRaw: String): TemplateProfile {
        val canonical = canonicalTemplateKey(template).ifBlank { "GENERAL" }
        val formId = formIdRaw.trim()
        var localForm = if (formId.isBlank()) null else appDb.agentFormDao().getFormById(formId)
        if (localForm == null && canonical.startsWith("CUSTOM_")) {
            val target = canonical.removePrefix("CUSTOM_")
            localForm = appDb.agentFormDao().getAllFormsOnce().firstOrNull {
                normalizeIdentifier(it.formId) == target
            }
        }

        val displayNameFromLocal = localForm?.title?.trim().orEmpty()
        val parsedConfig = localForm?.let { parseStoredConfig(it.fieldsJson) }
        val hasGoogleFromLocal = parsedConfig?.let { config ->
            config.verification.requireGoogleAuth || config.fields.any { it.type == FieldType.GOOGLE_AUTH }
        } ?: false
        val hasLocationFromLocal = parsedConfig?.let { config ->
            config.verification.requireLocationVerification || config.fields.any { it.type == FieldType.LOCATION }
        } ?: false
        val requiresContactFromLocal = parsedConfig?.let { config ->
            config.verification.requireContactVerification || config.fields.any { it.type == FieldType.CONTACT_PICKER }
        } ?: false

        val displayName = when {
            displayNameFromLocal.isNotBlank() -> displayNameFromLocal
            canonical == "CONTACT_VERIFY" -> "Contact Save Verification"
            canonical == "ADDRESS_LOCATION" -> "Address + Live Location Verification"
            canonical == "ADDRESS_BASIC" -> "Address Verification (Structured)"
            canonical == "EMAIL_SIGNIN" -> "Verified Google Email Capture"
            canonical == "BASIC_PROFILE_EMAIL" -> "Basic Profile Collection"
            canonical.startsWith("CUSTOM_") -> canonical.removePrefix("CUSTOM_").replace("_", " ")
            canonical.startsWith("FORM_") -> canonical.removePrefix("FORM_").replace("_", " ")
            canonical.startsWith("PRESET_") -> canonical.removePrefix("PRESET_").replace("_", " ")
            else -> canonical.replace("_", " ")
        }

        val requiresContact = when {
            canonical == "CONTACT_VERIFY" -> true
            localForm != null -> requiresContactFromLocal
            else -> false
        }
        val requiresGoogle = when {
            canonical == "EMAIL_SIGNIN" -> true
            localForm != null -> hasGoogleFromLocal
            else -> false
        }
        val requiresLocation = when {
            canonical == "ADDRESS_LOCATION" -> true
            localForm != null -> hasLocationFromLocal
            else -> false
        }

        val reservedNames = linkedSetOf<String>()
        val baseColumns = if (requiresContact) contactVerificationColumns else standardTemplateColumns
        baseColumns.forEach { reservedNames.add(it.name.lowercase(Locale.ROOT)) }
        if (requiresGoogle) reservedNames.add(COL_VERIFIED_GMAIL.lowercase(Locale.ROOT))
        if (requiresLocation) {
            reservedNames.add(COL_GPS_LOCATION.lowercase(Locale.ROOT))
            reservedNames.add(COL_GPS_LAT.lowercase(Locale.ROOT))
            reservedNames.add(COL_GPS_LNG.lowercase(Locale.ROOT))
            reservedNames.add(COL_GPS_ACC.lowercase(Locale.ROOT))
        }
        val fieldColumns = buildFieldColumns(
            fields = parsedConfig?.fields.orEmpty(),
            reservedNamesLower = reservedNames
        )

        return TemplateProfile(
            key = canonical,
            displayName = displayName.ifBlank { canonical },
            requiresContactVerification = requiresContact,
            requiresGoogleAuth = requiresGoogle,
            requiresLocationVerification = requiresLocation,
            fieldColumns = fieldColumns
        )
    }

    private fun buildTemplateColumnSpecs(profile: TemplateProfile): List<ColumnSpec> {
        val specs = mutableListOf<ColumnSpec>()
        val used = linkedSetOf<String>()

        val baseColumns = if (profile.requiresContactVerification) {
            contactVerificationColumns
        } else {
            standardTemplateColumns
        }

        baseColumns.forEach {
            specs.add(it)
            used.add(it.name.lowercase(Locale.ROOT))
        }

        profile.fieldColumns.forEach { field ->
            val normalized = field.columnName.trim().lowercase(Locale.ROOT)
            if (normalized.isBlank() || used.contains(normalized)) return@forEach
            specs.add(ColumnSpec(field.columnName, field.columnType))
            used.add(normalized)
        }

        if (profile.requiresGoogleAuth && !used.contains(COL_VERIFIED_GMAIL.lowercase(Locale.ROOT))) {
            specs.add(ColumnSpec(COL_VERIFIED_GMAIL, ColumnType.EMAIL))
            used.add(COL_VERIFIED_GMAIL.lowercase(Locale.ROOT))
        }

        if (profile.requiresLocationVerification) {
            if (!used.contains(COL_GPS_LOCATION.lowercase(Locale.ROOT))) {
                specs.add(ColumnSpec(COL_GPS_LOCATION, ColumnType.STRING))
                used.add(COL_GPS_LOCATION.lowercase(Locale.ROOT))
            }
            if (!used.contains(COL_GPS_LAT.lowercase(Locale.ROOT))) {
                specs.add(ColumnSpec(COL_GPS_LAT, ColumnType.STRING))
                used.add(COL_GPS_LAT.lowercase(Locale.ROOT))
            }
            if (!used.contains(COL_GPS_LNG.lowercase(Locale.ROOT))) {
                specs.add(ColumnSpec(COL_GPS_LNG, ColumnType.STRING))
                used.add(COL_GPS_LNG.lowercase(Locale.ROOT))
            }
            if (!used.contains(COL_GPS_ACC.lowercase(Locale.ROOT))) {
                specs.add(ColumnSpec(COL_GPS_ACC, ColumnType.STRING))
                used.add(COL_GPS_ACC.lowercase(Locale.ROOT))
            }
        }

        return specs
    }

    private fun buildTemplateSheetName(displayName: String): String {
        val cleaned = displayName.trim().ifBlank { "Template" }
        return cleaned.take(MAX_SHEET_NAME)
    }

    private fun buildTemplateDescription(profile: TemplateProfile): String {
        val safeName = profile.displayName.replace(";", ",")
        val contact = if (profile.requiresContactVerification) "1" else "0"
        val google = if (profile.requiresGoogleAuth) "1" else "0"
        val location = if (profile.requiresLocationVerification) "1" else "0"
        return "$TEMPLATE_KEY_META_PREFIX${profile.key};$TEMPLATE_CONTACT_META_PREFIX$contact;$TEMPLATE_GOOGLE_META_PREFIX$google;$TEMPLATE_LOCATION_META_PREFIX$location;title=$safeName"
    }

    private fun parseTemplateKeyFromDescription(description: String): String? {
        val part = description.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(TEMPLATE_KEY_META_PREFIX, ignoreCase = true) }
            ?: return null
        val key = part.substringAfter("=").trim()
        return canonicalTemplateKey(key)
    }

    private fun parseTemplateKeyFromSheetName(sheetName: String): String? {
        val trimmed = sheetName.trim()
        if (trimmed.isBlank()) return null
        if (!sheetName.startsWith(TEMPLATE_SHEET_PREFIX, ignoreCase = true)) return null
        if (sheetName.length <= TEMPLATE_SHEET_PREFIX.length) return null
        val suffix = sheetName.substring(TEMPLATE_SHEET_PREFIX.length).trim()
        if (suffix.isBlank()) return null
        return canonicalTemplateKey(suffix)
    }

    private fun parseTemplateKeyFromTable(table: TableModel): String? {
        val fromDescription = parseTemplateKeyFromDescription(table.description)
        if (!fromDescription.isNullOrBlank()) return fromDescription
        return parseTemplateKeyFromSheetName(table.name)
    }

    private suspend fun isContactVerificationSheet(sheet: TableModel): Boolean {
        val contactByDescription = sheet.description.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(TEMPLATE_CONTACT_META_PREFIX, ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()
        if (contactByDescription == "1") return true
        if (contactByDescription == "0") return false

        val cols = runCatching { columnDao.getColumnsByTableIdSync(sheet.id) }.getOrElse { emptyList() }
        return cols.any { it.name.equals("Contact Saved", ignoreCase = true) }
    }

    private suspend fun ensureSheet(
        folderId: Long,
        name: String,
        description: String,
        specs: List<ColumnSpec>
    ): TableModel {
        val sameNamed = tableDao.getTablesByFolderIdSync(folderId).filter { it.name.equals(name, ignoreCase = true) }
        val existing = sameNamed.maxByOrNull { it.updatedAt }

        if (sameNamed.size > 1 && existing != null) {
            sameNamed.forEach { table ->
                if (table.id != existing.id) {
                    tableDao.deleteTableById(table.id)
                }
            }
        }

        if (existing == null) {
            return createSheet(folderId, name, description, specs)
        }

        var working = existing
        if (!working.name.equals(name, ignoreCase = false) || working.description != description) {
            tableDao.updateTable(
                working.copy(
                    name = name,
                    description = description,
                    updatedAt = System.currentTimeMillis()
                )
            )
            working = requireNotNull(tableDao.getTableById(working.id))
        }

        val existingColumns = columnDao.getColumnsByTableIdSync(working.id).sortedBy { it.orderIndex }
        val remaining = existingColumns.toMutableList()
        val ordered = mutableListOf<ColumnModel>()

        specs.forEachIndexed { index, spec ->
            val matchIndex = remaining.indexOfFirst { it.name.equals(spec.name, ignoreCase = true) }
            val matched = if (matchIndex >= 0) remaining.removeAt(matchIndex) else null

            if (matched == null) {
                val insertedId = columnDao.insertColumn(
                    ColumnModel(
                        tableId = working.id,
                        name = spec.name,
                        type = spec.type,
                        orderIndex = index,
                        selectOptions = spec.selectOptions
                    )
                )
                ordered.add(
                    ColumnModel(
                        id = insertedId,
                        tableId = working.id,
                        name = spec.name,
                        type = spec.type,
                        orderIndex = index,
                        selectOptions = spec.selectOptions
                    )
                )
            } else {
                val updated = matched.copy(
                    name = spec.name,
                    type = spec.type,
                    selectOptions = spec.selectOptions
                )
                if (
                    matched.name != updated.name ||
                    matched.type != updated.type ||
                    matched.selectOptions != updated.selectOptions
                ) {
                    columnDao.updateColumn(updated)
                }
                ordered.add(updated)
            }
        }

        ordered.addAll(remaining.sortedBy { it.orderIndex })
        ordered.forEachIndexed { index, column ->
            if (column.orderIndex != index) {
                columnDao.updateColumnOrder(column.id, index)
            }
        }

        ensureMinRows(working.id)
        tableDao.updateColumnCount(working.id, ordered.size)
        return requireNotNull(tableDao.getTableById(working.id))
    }

    private suspend fun createSheet(
        folderId: Long,
        name: String,
        description: String,
        specs: List<ColumnSpec>
    ): TableModel {
        val tableId = tableDao.insertTable(
            TableModel(
                name = name,
                description = description,
                folderId = folderId,
                columnCount = specs.size,
                rowCount = PREBUILT_ROWS
            )
        )

        specs.forEachIndexed { index, spec ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = spec.name,
                    type = spec.type,
                    orderIndex = index,
                    selectOptions = spec.selectOptions
                )
            )
        }

        repeat(PREBUILT_ROWS) { index ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = index))
        }

        return requireNotNull(tableDao.getTableById(tableId))
    }

    private suspend fun ensureMinRows(tableId: Long) {
        val rows = rowDao.getRowsByTableIdSync(tableId)
        if (rows.size >= MIN_BUFFER_ROWS) return

        val maxOrder = rowDao.getMaxOrderIndex(tableId) ?: -1
        repeat((MIN_BUFFER_ROWS - rows.size).coerceAtLeast(0)) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrder + i + 1))
        }
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
    }

    private suspend fun upsertRecord(sheet: TableModel, data: Map<String, String>): Boolean {
        val columns = columnDao.getColumnsByTableIdSync(sheet.id)
        if (columns.isEmpty()) return false

        val recordId = data["Record ID"].orEmpty().trim()
        if (recordId.isBlank()) return false
        val recordCol = columns.firstOrNull { it.name == "Record ID" } ?: return false

        var rowId = cellDao.findCellsByColumnAndValue(recordCol.id, recordId).firstOrNull()?.rowId
        if (rowId == null) rowId = findOrCreateRow(sheet.id)
        if (rowId == null) return false

        data.forEach { (key, value) ->
            val col = columns.firstOrNull { it.name == key } ?: return@forEach
            val existing = cellDao.getCellSync(rowId, col.id)
            if (existing == null) {
                cellDao.insertCell(CellModel(rowId = rowId, columnId = col.id, value = value))
            } else if (existing.value != value) {
                cellDao.updateCell(existing.copy(value = value))
            }
        }

        return true
    }

    private suspend fun findOrCreateRow(tableId: Long): Long? {
        val rows = rowDao.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
        for (row in rows) {
            val cells = cellDao.getCellsByRowIdSync(row.id)
            if (cells.isEmpty() || cells.all { it.value.isBlank() }) return row.id
        }

        val maxOrder = rowDao.getMaxOrderIndex(tableId) ?: -1
        var first: Long? = null
        repeat(EXPAND_ROWS) { i ->
            val id = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrder + i + 1))
            if (first == null) first = id
        }
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
        return first
    }

    private suspend fun loadValuesByRecord(sheet: TableModel, recordId: String): Map<String, String> {
        val columns = columnDao.getColumnsByTableIdSync(sheet.id)
        val recordCol = columns.firstOrNull { it.name == "Record ID" } ?: return emptyMap()
        val rowId = cellDao.findCellsByColumnAndValue(recordCol.id, recordId).firstOrNull()?.rowId ?: return emptyMap()
        val columnNameById = columns.associateBy({ it.id }, { it.name })
        return cellDao.getCellsByRowIdSync(rowId).associate { columnNameById[it.columnId].orEmpty() to it.value }
    }

    private suspend fun cleanupDuplicateRows(sheet: TableModel): Int {
        val columns = columnDao.getColumnsByTableIdSync(sheet.id)
        if (columns.isEmpty()) return 0

        val colByName = columns.associateBy { it.name }
        val numberCol = colByName["Number"] ?: return 0
        val timeCol = colByName["Date Time"] ?: return 0
        val recordCol = colByName["Record ID"] ?: return 0

        data class Holder(val rowId: Long, val recordId: String, val ts: Long)
        val keepByKey = linkedMapOf<String, Holder>()
        val removeRowIds = linkedSetOf<Long>()

        rowDao.getRowsByTableIdSync(sheet.id).forEach { row ->
            val cells = cellDao.getCellsByRowIdSync(row.id)
            if (cells.isEmpty()) return@forEach
            val values = cells.associateBy({ it.columnId }, { it.value })
            val number = sanitizePhone(values[numberCol.id])
            val recordId = values[recordCol.id].orEmpty().trim()

            if (number.isBlank() || recordId.isBlank()) {
                return@forEach
            }

            val key = number
            val ts = parseAnyTime(values[timeCol.id])
            val current = Holder(row.id, recordId, ts)
            val existing = keepByKey[key]
            if (existing == null) {
                keepByKey[key] = current
            } else {
                val keepCurrent = current.ts >= existing.ts
                if (keepCurrent) {
                    removeRowIds.add(existing.rowId)
                    keepByKey[key] = current
                } else {
                    removeRowIds.add(current.rowId)
                }
            }
        }

        removeRowIds.forEach { rowId ->
            cellDao.deleteCellsByRowId(rowId)
            rowDao.deleteRowById(rowId)
        }

        if (removeRowIds.isNotEmpty()) {
            tableDao.updateRowCount(sheet.id, rowDao.getRowCountSync(sheet.id))
            ensureMinRows(sheet.id)
        }

        return removeRowIds.size
    }

    private suspend fun resolveName(
        data: Map<String, Any>,
        targetPhone: String,
        cache: MutableMap<String, String>
    ): String {
        val direct = firstNonBlank(
            readString(data["name"]),
            readString(data["full_name"]),
            readString(data["customer_name"]),
            readString(data["customerName"]),
            readString(data["user_name"]),
            readString(data["userName"])
        )
        if (direct.isNotBlank()) return direct

        val first = firstNonBlank(readString(data["first_name"]), readString(data["firstName"]))
        val last = firstNonBlank(readString(data["last_name"]), readString(data["lastName"]))
        val full = "$first $last".trim()
        if (full.isNotBlank()) return full

        val metadata = readMap(data["metadata"])
        val google = firstNonBlank(
            readString(readMap(data["google_user"])["name"]),
            readString(readMap(metadata["google_user"])["name"])
        )
        if (google.isNotBlank()) return google

        if (targetPhone.isBlank()) return ""
        cache[targetPhone]?.let { return it }

        val fromDb = resolveNameFromContactDb(targetPhone)
        if (fromDb.isNotBlank()) {
            cache[targetPhone] = fromDb
            return fromDb
        }

        val fromLogs = resolveSenderNameFromLogs(targetPhone)
        cache[targetPhone] = fromLogs
        return fromLogs
    }

    private suspend fun resolveSenderNameFromLogs(targetPhone: String): String {
        for (variant in phoneVariants(targetPhone)) {
            val sender =
                messageRepository.getRecentMessagesSync(variant, 20)
                    .firstOrNull { it.senderName.isNotBlank() }
                    ?.senderName
                    .orEmpty()
                    .trim()
            if (sender.isNotBlank()) return sender
        }
        return ""
    }

    private suspend fun resolveNameFromContactDb(targetPhone: String): String {
        val map = ensureContactNameCache()
        for (variant in phoneVariants(targetPhone)) {
            map[variant]?.let { if (it.isNotBlank()) return it }
        }
        return ""
    }

    private suspend fun ensureContactNameCache(): Map<String, String> {
        contactNameCache?.let { return it }
        val out = linkedMapOf<String, String>()
        runCatching {
            contactGroupDao.getAllGroupsList().forEach { group ->
                group.contacts.forEach { contact ->
                    val phone = sanitizePhone(contact.number)
                    val name = contact.name.trim()
                    if (phone.isBlank() || name.isBlank()) return@forEach
                    out.putIfAbsent(phone, name)
                    if (phone.length > 10) out.putIfAbsent(phone.takeLast(10), name)
                }
            }
        }.onFailure {
            Log.w(TAG, "contact cache load failed: ${it.message}")
        }
        contactNameCache = out
        return out
    }

    private fun resolveVerificationStatus(data: Map<String, Any>): String {
        val metadata = readMap(data["metadata"])
        return firstNonBlank(
            readString(data["verificationStatus"]),
            readString(data["contact_verification_status"]),
            readString(metadata["contact_verification_status"]),
            readString(data["submissionStatus"]),
            "pending"
        )
    }

    private fun mapContactSaved(status: String): String {
        return when (status.trim().lowercase(Locale.ROOT)) {
            "saved", "saved_verified", "verified", "matched", "success" -> SAVE
            "not_saved", "failed", "mismatch", "not_verified", "rejected" -> NOT_SAVE
            else -> PENDING
        }
    }

    private fun resolveTemplateKey(campaignRaw: String, formId: String): String {
        val campaign = campaignRaw.trim()
        if (campaign.lowercase(Locale.ROOT).startsWith("ai_agent_")) {
            val stripped = campaign.substring(9)
            val normalized = canonicalTemplateKey(stripped)
            if (normalized.isNotBlank()) return normalized
        }

        if (formId.isNotBlank()) {
            val known = PRESET_TEMPLATE_KEYS[formId.lowercase(Locale.ROOT)]
            if (!known.isNullOrBlank()) return known
            if (formId.startsWith("preset-", ignoreCase = true)) {
                return "PRESET_${normalizeIdentifier(formId.removePrefix("preset-"))}"
            }
            return "CUSTOM_${normalizeIdentifier(formId)}"
        }

        if (campaign.isNotBlank()) {
            val normalized = canonicalTemplateKey(campaign)
            if (normalized.isNotBlank()) return normalized
        }

        return "GENERAL"
    }

    private fun canonicalTemplateKey(raw: String): String {
        val normalized = normalizeIdentifier(raw)
        if (normalized.isBlank()) return ""
        if (normalized.startsWith("FORM_")) {
            return "CUSTOM_${normalized.removePrefix("FORM_")}"
        }
        return when (normalized) {
            "CONTACT_SAVE_VERIFICATION", "CONTACT_VERIFICATION", "CONTACT_VERIFY" -> "CONTACT_VERIFY"
            "ADDRESS_MAPS_FILLUP", "ADDRESS_MAP", "ADDRESS_MAPS", "ADDRESS_LOCATION", "LOCATION_ADDRESS" -> "ADDRESS_LOCATION"
            "ADDRESS_VERIFICATION", "ADDRESS_BASIC", "ADDRESS_VERIFY" -> "ADDRESS_BASIC"
            "GOOGLE_SIGNIN_EMAIL", "GOOGLE_EMAIL", "GOOGLE_SIGNIN", "GOOGLE_SIGN_IN", "EMAIL_SIGNIN" -> "EMAIL_SIGNIN"
            else -> normalized
        }
    }

    private fun isAiCampaign(campaignRaw: String): Boolean {
        return campaignRaw.trim().lowercase(Locale.ROOT).startsWith("ai_agent_")
    }

    private fun isAiTemplateKey(templateKey: String): Boolean {
        val normalized = templateKey.trim().uppercase(Locale.ROOT)
        if (normalized.isBlank()) return false
        if (normalized.startsWith("FORM_")) return false
        if (normalized == "GENERAL") return false
        if (normalized == "ANDROID_BUILDER") return false
        return true
    }

    private fun buildFieldColumns(
        fields: List<FormField>,
        reservedNamesLower: MutableSet<String>
    ): List<FormFieldColumn> {
        val out = mutableListOf<FormFieldColumn>()
        fields.forEach { field ->
            val fieldId = field.id.trim()
            if (fieldId.isBlank()) return@forEach

            if (
                field.type == FieldType.CONTACT_PICKER ||
                field.type == FieldType.GOOGLE_AUTH ||
                field.type == FieldType.LOCATION
            ) {
                return@forEach
            }

            val preferredName = field.label.trim().ifBlank { toDisplayFieldName(fieldId) }
            val columnName = uniqueColumnName(preferredName, reservedNamesLower)
            out.add(
                FormFieldColumn(
                    fieldId = fieldId,
                    columnName = columnName,
                    columnType = toColumnType(field.type)
                )
            )
        }
        return out
    }

    private fun toColumnType(type: FieldType): String {
        return when (type) {
            FieldType.PHONE -> ColumnType.PHONE
            FieldType.EMAIL -> ColumnType.EMAIL
            FieldType.NUMBER -> ColumnType.INTEGER
            FieldType.DATE -> ColumnType.DATE
            FieldType.TIME -> ColumnType.TIME
            else -> ColumnType.STRING
        }
    }

    private fun parseStoredConfig(fieldsJson: String): StoredFormConfig {
        if (fieldsJson.isBlank()) return StoredFormConfig()
        return runCatching {
            val trimmed = fieldsJson.trim()
            val parsed = if (trimmed.startsWith("[")) {
                val fields: List<FormField> = gson.fromJson(
                    trimmed,
                    object : TypeToken<List<FormField>>() {}.type
                ) ?: emptyList()
                StoredFormConfig(fields = fields)
            } else {
                gson.fromJson(trimmed, StoredFormConfig::class.java) ?: StoredFormConfig()
            }
            foldLegacyVerification(parsed)
        }.getOrElse { StoredFormConfig() }
    }

    private fun foldLegacyVerification(config: StoredFormConfig): StoredFormConfig {
        val hasLegacyGoogleAuth = config.fields.any { it.type == FieldType.GOOGLE_AUTH }
        val hasLegacyContactPicker = config.fields.any { it.type == FieldType.CONTACT_PICKER }
        val hasLegacyLocation = config.fields.any { it.type == FieldType.LOCATION }

        val cleanedFields = config.fields.filterNot {
            it.type == FieldType.GOOGLE_AUTH ||
                it.type == FieldType.CONTACT_PICKER ||
                it.type == FieldType.LOCATION
        }

        return config.copy(
            fields = cleanedFields,
            verification = config.verification.copy(
                requireGoogleAuth = config.verification.requireGoogleAuth || hasLegacyGoogleAuth,
                requireContactVerification = config.verification.requireContactVerification || hasLegacyContactPicker,
                requireLocationVerification = config.verification.requireLocationVerification || hasLegacyLocation
            )
        )
    }

    private fun requiresContactVerification(fieldsJson: String): Boolean {
        if (fieldsJson.isBlank()) return false
        return runCatching {
            val config = parseStoredConfig(fieldsJson)
            config.verification.requireContactVerification ||
                config.fields.any { it.type == FieldType.CONTACT_PICKER }
        }.getOrElse {
            false
        }
    }

    private suspend fun resolveOwnerPhone(uid: String): String {
        val prefs = UserDetailsPreferences(appContext)
        var phone = sanitizePhone(prefs.getPhoneNumber())
        if (phone.isNotBlank()) return phone
        if (uid.isBlank()) return ""

        return try {
            val fetched = firestore.collection("userDetails")
                .document(uid)
                .get()
                .await()
                .getString("phoneNumber")
            phone = sanitizePhone(fetched)
            if (phone.isNotBlank()) prefs.updatePhoneNumber(phone)
            phone
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun deleteTemplateDataViaWorker(uid: String, ownerPhone: String, template: String): Int {
        return try {
            val body = JSONObject().apply {
                put("uid", uid)
                put("phone", ownerPhone)
                put("templateKey", template)
            }

            val request = Request.Builder()
                .url(DELETE_TEMPLATE_API_URL)
                .post(
                    body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Worker template delete API failed: ${response.code}")
                    return 0
                }
                val text = response.body?.string().orEmpty()
                val json = JSONObject(text.ifBlank { "{}" })
                json.optInt("deletedCount", 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteTemplateDataViaWorker failed: ${e.message}", e)
            0
        }
    }

    private fun verificationRecordId(ownerUid: String, template: String, targetPhone: String): String {
        fun part(value: String): String {
            return value.trim().ifBlank { "NA" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(64)
        }
        return "AFR2:${part(ownerUid)}:${part(template)}:${part(targetPhone)}"
    }

    private fun normalizeIdentifier(raw: String): String {
        return raw.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .uppercase(Locale.ROOT)
            .take(44)
    }

    private fun parseTimestamp(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: parseAnyTime(value)
            else -> System.currentTimeMillis()
        }
    }

    private fun parseAnyTime(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return 0L
        raw.toLongOrNull()?.let { if (it > 0L) return it }

        val patterns = listOf(
            "dd MMM yyyy, hh:mm:ss a",
            "dd MMM yyyy, hh:mm a",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd hh:mm:ss a",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        patterns.forEach { pattern ->
            try {
                val parser = SimpleDateFormat(pattern, Locale.US)
                if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                }
                parser.parse(raw)?.let { return it.time }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun displayTime(value: Long): String {
        if (value <= 0L) return ""
        return SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(value))
    }

    private fun readString(value: Any?): String = value?.toString()?.trim().orEmpty()

    private fun readMap(value: Any?): Map<String, Any?> {
        if (value !is Map<*, *>) return emptyMap()
        return value.entries.mapNotNull { entry ->
            val key = entry.key?.toString()?.trim().orEmpty()
            if (key.isBlank()) null else key to entry.value
        }.toMap()
    }

    private fun firstNonBlank(vararg values: String): String {
        values.forEach { if (it.isNotBlank()) return it }
        return ""
    }

    private fun sanitizePhone(value: String?): String {
        return value.orEmpty().replace(Regex("[^0-9]"), "")
    }

    private fun phoneVariants(phone: String): List<String> {
        val normalized = sanitizePhone(phone)
        if (normalized.isBlank()) return emptyList()

        val set = linkedSetOf<String>()
        set.add(normalized)
        set.add("+$normalized")
        if (normalized.length > 10) {
            val last10 = normalized.takeLast(10)
            set.add(last10)
            set.add("+$last10")
        }
        return set.toList()
    }
}
