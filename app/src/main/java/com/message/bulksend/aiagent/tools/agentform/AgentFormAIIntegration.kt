package com.message.bulksend.aiagent.tools.agentform

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormContactSettings
import com.message.bulksend.aiagent.tools.agentform.models.FieldType
import com.message.bulksend.aiagent.tools.agentform.models.FormField
import com.message.bulksend.aiagent.tools.agentform.models.FormVerificationSettings
import com.message.bulksend.aiagent.tools.agentform.models.StoredFormConfig
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.userdetails.UserDetailsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

data class AgentFormLinkResult(
    val success: Boolean,
    val message: String,
    val url: String = "",
    val campaign: String = "",
    val formId: String = "",
    val templateKey: String = "",
    val requiresContactVerification: Boolean = false
)

data class AgentFormResponseSnapshot(
    val status: String,
    val eventType: String,
    val timestamp: Long,
    val summary: String
)

data class AgentFormVerificationStatusSnapshot(
    val hasEvents: Boolean,
    val formOpened: Boolean,
    val verified: Boolean,
    val submitted: Boolean,
    val latestStatus: String,
    val latestEventType: String,
    val latestTimestamp: Long,
    val summary: String
)

class AgentFormAIIntegration(private val context: Context) {

    companion object {
        private const val TAG = "AgentFormAIIntegration"
        private const val PREFS = "agent_form_ai_state"
        private const val KEY_LINK_PREFIX = "last_link_"
    }

    private data class TemplateDesc(
        val key: String,
        val formId: String,
        val title: String,
        val description: String,
        val isCustom: Boolean = false
    )

    private val predefinedTemplates = listOf(
        TemplateDesc(
            key = "ADDRESS_LOCATION",
            formId = "preset-address-maps-fillup",
            title = "Address Fillup with Google Maps",
            description = "Address + live location verification"
        ),
        TemplateDesc(
            key = "ADDRESS_BASIC",
            formId = "preset-address-verification",
            title = "Address Verification",
            description = "Structured address collection"
        ),
        TemplateDesc(
            key = "CONTACT_VERIFY",
            formId = "preset-contact-save-verification",
            title = "Contact Save Verification",
            description = "Verify contact save in phone"
        ),
        TemplateDesc(
            key = "EMAIL_SIGNIN",
            formId = "preset-google-signin-email",
            title = "Google Sign-In Email",
            description = "Google auth + verified email"
        )
    )

    private val gson = Gson()
    private val firestore = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()
    private val tableSheetSyncManager = AgentFormTableSheetSyncManager(context.applicationContext)

    suspend fun getAgentFormContextForAI(recipientPhoneRaw: String): String = withContext(Dispatchers.IO) {
        val phone = sanitizePhone(recipientPhoneRaw)
        val latest = if (phone.isBlank()) null else getLatestResponseForRecipient(phone, syncToSheet = false)
        val lastLink = if (phone.isBlank()) "" else getLastLink(phone)
        val templates = loadAvailableTemplates()

        buildString {
            append("Use AgentForm templates from the available key list below.\n")
            append("Commands:\n")
            append("- [SEND_AGENT_FORM: TEMPLATE_KEY]\n")
            append("- [CHECK_AGENT_FORM_RESPONSE]\n")
            append("Template Keys:\n")
            templates.forEach { t ->
                val type = if (t.isCustom) "custom" else "predefined"
                append("- ${t.key} ($type): ${t.title}\n")
            }
            if (lastLink.isNotBlank()) append("Last sent form link for this user: $lastLink\n")
            if (latest != null) {
                append("Latest response status: ${latest.status} (${latest.eventType}) at ${formatTime(latest.timestamp)}\n")
                if (latest.summary.isNotBlank()) append("Latest response data: ${latest.summary}\n")
            } else if (phone.isNotBlank()) {
                append("Latest response status: no response yet.\n")
            }
        }
    }

    suspend fun createFormLinkForRecipient(templateKeyRaw: String, recipientPhoneRaw: String): AgentFormLinkResult = withContext(Dispatchers.IO) {
        try {
            AgentFormPredefinedTemplates.seedIfNeeded(context)
            val templates = loadAvailableTemplates()

            val recipientPhone = sanitizePhone(recipientPhoneRaw)
            if (recipientPhone.isBlank()) {
                return@withContext AgentFormLinkResult(false, "Recipient phone number unavailable for AgentForm.")
            }

            val template = resolveTemplate(templateKeyRaw, templates)
                ?: return@withContext AgentFormLinkResult(false, "Invalid template key. Available keys: ${templates.joinToString { it.key }}")

            val ownerUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().trim()
            val ownerPhone = resolveOwnerPhone(ownerUid)
            if (ownerUid.isBlank() || ownerPhone.isBlank()) {
                return@withContext AgentFormLinkResult(false, "Owner UID/phone missing. Login required for AgentForm.")
            }

            val form = AppDatabase.getInstance(context).agentFormDao().getFormById(template.formId)
                ?: return@withContext AgentFormLinkResult(false, "Template not available locally: ${template.key}")

            val config = parseConfig(form.fieldsJson)
            val isContactVerificationTemplate = requiresContactVerification(config)
            val contactSettings = AgentFormContactSettingsManager(context).getSettings()
            if (isContactVerificationTemplate && !hasValidSavedContacts(contactSettings)) {
                return@withContext AgentFormLinkResult(
                    success = false,
                    message = "Contact Verify template ke liye saved contacts missing hain. AgentForm me contact settings enable karke at least 1 contact save karein."
                )
            }
            val effectiveContactSettings = if (isContactVerificationTemplate) {
                contactSettings
            } else {
                contactSettings.copy(
                    enabled = false,
                    contacts = emptyList(),
                    postSubmitContentEnabled = false,
                    postSubmitVideoUrl = "",
                    postSubmitPdfs = emptyList()
                )
            }
            val campaign = buildAiCampaignKey(template)
            val apiResult = createCloudForm(
                uid = ownerUid,
                ownerPhone = ownerPhone,
                targetPhone = recipientPhone,
                title = form.title,
                description = form.description,
                fields = config.fields,
                verification = config.verification,
                contactSettings = effectiveContactSettings,
                campaign = campaign
            ) ?: return@withContext AgentFormLinkResult(false, "Failed to create AgentForm link.")

            val link = if (apiResult.second.isNotBlank()) apiResult.second else buildLink(
                formId = apiResult.first,
                uid = ownerUid,
                ownerPhone = ownerPhone,
                campaign = campaign,
                targetPhone = recipientPhone
            )

            saveLastLink(recipientPhone, link)
            runCatching {
                tableSheetSyncManager.logFormLinkSent(
                    ownerUid = ownerUid,
                    ownerPhone = ownerPhone,
                    targetPhoneRaw = recipientPhone,
                    formIdRaw = apiResult.first,
                    campaignRaw = campaign,
                    formLinkRaw = link
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to log AgentForm link in TableSheet: ${error.message}", error)
            }
            AgentFormLinkResult(
                success = true,
                message = "Please fill this secure form so I can continue:\n$link",
                url = link,
                campaign = campaign,
                formId = apiResult.first,
                templateKey = template.key,
                requiresContactVerification = isContactVerificationTemplate
            )
        } catch (e: Exception) {
            Log.e(TAG, "createFormLinkForRecipient failed: ${e.message}", e)
            AgentFormLinkResult(false, "AgentForm link generation failed: ${e.message}")
        }
    }

    suspend fun buildLatestResponseMessage(recipientPhoneRaw: String): String = withContext(Dispatchers.IO) {
        val phone = sanitizePhone(recipientPhoneRaw)
        if (phone.isBlank()) return@withContext "Recipient phone unavailable, response check skipped."

        val latest = getLatestResponseForRecipient(phone)
        if (latest != null) {
            return@withContext "Form response receive ho gaya hai (${latest.status}) on ${formatTime(latest.timestamp)}. ${latest.summary}".trim()
        }

        val link = getLastLink(phone)
        if (link.isNotBlank()) {
            "Abhi tak form response receive nahi hua. Last sent form link: $link"
        } else {
            "Is user ke liye abhi tak koi form send nahi kiya gaya."
        }
    }

    suspend fun getLatestResponseForRecipient(
        recipientPhoneRaw: String,
        syncToSheet: Boolean = true
    ): AgentFormResponseSnapshot? = withContext(Dispatchers.IO) {
        try {
            val recipientPhone = sanitizePhone(recipientPhoneRaw)
            if (recipientPhone.isBlank()) return@withContext null

            val ownerUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().trim()
            val ownerPhone = resolveOwnerPhone(ownerUid)
            if (ownerUid.isBlank() || ownerPhone.isBlank()) return@withContext null

            val snapshot = firestore.collection("users")
                .document(ownerUid)
                .collection("numbers")
                .document(ownerPhone)
                .collection("responses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .await()

            if (syncToSheet) {
                runCatching {
                    tableSheetSyncManager.syncResponseDocuments(
                        ownerUid = ownerUid,
                        ownerPhone = ownerPhone,
                        documents = snapshot.documents
                    )
                }.onFailure { error ->
                    Log.e(TAG, "AgentForm sheet sync failed: ${error.message}", error)
                }
            }

            val latest = snapshot.documents
                .mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val targetPhone = sanitizePhone(data["targetPhone"]?.toString() ?: data["target_phone"]?.toString() ?: "")
                    if (targetPhone != recipientPhone) return@mapNotNull null

                    val status = data["verificationStatus"]?.toString()
                        ?: data["submissionStatus"]?.toString()
                        ?: "received"
                    val eventType = data["eventType"]?.toString() ?: "submission"
                    val timestamp = parseTimestamp(data["timestamp"])
                    val summary = buildSummary(data)

                    AgentFormResponseSnapshot(status, eventType, timestamp, summary)
                }
                .maxByOrNull { it.timestamp }

            latest
        } catch (e: Exception) {
            Log.e(TAG, "getLatestResponseForRecipient failed: ${e.message}", e)
            null
        }
    }

    suspend fun getRecipientVerificationStatus(
        recipientPhoneRaw: String,
        campaignFilterRaw: String = "",
        formIdFilterRaw: String = "",
        syncToSheet: Boolean = true
    ): AgentFormVerificationStatusSnapshot? = withContext(Dispatchers.IO) {
        try {
            val recipientPhone = sanitizePhone(recipientPhoneRaw)
            if (recipientPhone.isBlank()) return@withContext null

            val ownerUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().trim()
            val ownerPhone = resolveOwnerPhone(ownerUid)
            if (ownerUid.isBlank() || ownerPhone.isBlank()) return@withContext null

            val campaignFilter = campaignFilterRaw.trim()
            val formIdFilter = formIdFilterRaw.trim()

            val snapshot = firestore.collection("users")
                .document(ownerUid)
                .collection("numbers")
                .document(ownerPhone)
                .collection("responses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(250)
                .get()
                .await()

            if (syncToSheet) {
                runCatching {
                    tableSheetSyncManager.syncResponseDocuments(
                        ownerUid = ownerUid,
                        ownerPhone = ownerPhone,
                        documents = snapshot.documents
                    )
                }.onFailure { error ->
                    Log.e(TAG, "AgentForm sheet sync failed: ${error.message}", error)
                }
            }

            var hasEvents = false
            var formOpened = false
            var verified = false
            var submitted = false
            var latestStatus = "pending"
            var latestEventType = "none"
            var latestTimestamp = 0L
            var latestSummary = ""

            snapshot.documents.forEach { doc ->
                val data = doc.data ?: return@forEach
                val targetPhone = sanitizePhone(
                    data["targetPhone"]?.toString() ?: data["target_phone"]?.toString() ?: ""
                )
                if (targetPhone != recipientPhone) return@forEach

                val campaign = data["campaign"]?.toString().orEmpty().trim()
                if (!campaign.lowercase(Locale.ROOT).startsWith("ai_agent_")) return@forEach
                if (campaignFilter.isNotBlank() && !campaign.equals(campaignFilter, ignoreCase = true)) {
                    return@forEach
                }

                val formId = data["form_id"]?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: data["formId"]?.toString().orEmpty()
                if (formIdFilter.isNotBlank() && !formId.equals(formIdFilter, ignoreCase = true)) {
                    return@forEach
                }

                hasEvents = true
                val eventType = data["eventType"]?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    .ifBlank { "submission" }
                val status = resolveVerificationStatus(data)
                val timestamp = parseTimestamp(data["timestamp"])

                if (eventType == "form_open" || eventType == "submission" || eventType == "contact_verification") {
                    formOpened = true
                }
                if (eventType == "submission") {
                    submitted = true
                }
                if (isVerifiedStatus(status)) {
                    verified = true
                }

                if (timestamp >= latestTimestamp) {
                    latestTimestamp = timestamp
                    latestStatus = status.ifBlank { latestStatus }
                    latestEventType = eventType
                    latestSummary = buildSummary(data)
                }
            }

            if (!hasEvents) {
                return@withContext AgentFormVerificationStatusSnapshot(
                    hasEvents = false,
                    formOpened = false,
                    verified = false,
                    submitted = false,
                    latestStatus = "no_response",
                    latestEventType = "none",
                    latestTimestamp = 0L,
                    summary = ""
                )
            }

            AgentFormVerificationStatusSnapshot(
                hasEvents = true,
                formOpened = formOpened,
                verified = verified,
                submitted = submitted,
                latestStatus = latestStatus.ifBlank { "pending" },
                latestEventType = latestEventType.ifBlank { "submission" },
                latestTimestamp = latestTimestamp,
                summary = latestSummary
            )
        } catch (e: Exception) {
            Log.e(TAG, "getRecipientVerificationStatus failed: ${e.message}", e)
            null
        }
    }

    private suspend fun createCloudForm(
        uid: String,
        ownerPhone: String,
        targetPhone: String,
        title: String,
        description: String,
        fields: List<FormField>,
        verification: FormVerificationSettings,
        contactSettings: AgentFormContactSettings,
        campaign: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = JSONObject().apply {
            put("uid", uid)
            put("phone", ownerPhone)
            put("targetPhone", targetPhone)
            put("title", title)
            put("description", description)
            put("fields", buildCloudFields(fields))
            put("verification", buildVerificationJson(verification, contactSettings))
            put("contactVerification", buildContactVerificationJson(verification, contactSettings))
            put("postSubmitContent", buildPostSubmitContentJson(contactSettings))
            put("campaign", campaign)
        }

        val request = Request.Builder()
            .url("https://chataiform.com/api/create-form")
            .post(body.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "create-form API failed: ${response.code}")
                return@withContext null
            }

            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val formId = json.optString("formId").trim()
            val url = json.optString("url").trim()
            if (formId.isBlank()) null else formId to url
        }
    }

    private fun buildCloudFields(fields: List<FormField>): JSONArray {
        val array = JSONArray()
        fields.forEach { field ->
            array.put(JSONObject().apply {
                put("id", field.id)
                put("type", toCloudType(field.type))
                put("label", field.label)
                put("required", field.required)
                if (field.options.isNotEmpty()) {
                    val options = JSONArray()
                    field.options.forEach { options.put(it) }
                    put("options", options)
                }
                if (field.hint.isNotBlank()) put("hint", field.hint)
            })
        }
        return array
    }

    private suspend fun loadAvailableTemplates(): List<TemplateDesc> {
        val out = mutableListOf<TemplateDesc>()
        out.addAll(predefinedTemplates)

        val usedFormIds = LinkedHashSet<String>()
        predefinedTemplates.forEach { usedFormIds.add(it.formId) }

        val forms = AppDatabase.getInstance(context).agentFormDao().getAllFormsOnce()
        forms.forEach { form ->
            val formId = form.formId.trim()
            if (formId.isBlank() || usedFormIds.contains(formId)) return@forEach
            usedFormIds.add(formId)

            val customKey = "FORM_${normalizeIdentifier(formId)}"
            val title = form.title.trim().ifBlank { "Custom Form" }
            val description = form.description.trim().ifBlank { "Custom template" }
            out.add(
                TemplateDesc(
                    key = customKey,
                    formId = formId,
                    title = title,
                    description = description,
                    isCustom = true
                )
            )
        }
        return out
    }

    private fun resolveTemplate(raw: String, templates: List<TemplateDesc>): TemplateDesc? {
        if (templates.isEmpty()) return null

        val key = normalizeTemplateKey(raw)
        templates.firstOrNull { it.key == key }?.let { return it }

        val rawTrimmed = raw.trim()
        if (rawTrimmed.isNotBlank()) {
            templates.firstOrNull { it.formId.equals(rawTrimmed, ignoreCase = true) }?.let { return it }
        }

        val normalizedRaw = normalizeIdentifier(rawTrimmed)
        if (normalizedRaw.isNotBlank()) {
            templates.firstOrNull { normalizeIdentifier(it.title) == normalizedRaw }?.let { return it }
            templates.firstOrNull { normalizeIdentifier(it.title).contains(normalizedRaw) }?.let { return it }
            templates.firstOrNull { normalizeIdentifier(it.formId) == normalizedRaw }?.let { return it }
        }

        return null
    }

    private fun normalizeTemplateKey(raw: String): String {
        val key = raw.trim().uppercase(Locale.ROOT).replace("-", "_").replace(" ", "_")
        return when (key) {
            "ADDRESS", "ADDRESS_VERIFY", "ADDRESS_VERIFICATION" -> "ADDRESS_BASIC"
            "ADDRESS_MAP", "ADDRESS_MAPS", "LOCATION_ADDRESS", "ADDRESS_WITH_LOCATION" -> "ADDRESS_LOCATION"
            "CONTACT", "CONTACT_VERIFICATION", "CONTACT_SAVE_VERIFICATION" -> "CONTACT_VERIFY"
            "EMAIL", "GMAIL", "GOOGLE", "GOOGLE_SIGNIN", "GOOGLE_SIGN_IN", "GOOGLE_EMAIL" -> "EMAIL_SIGNIN"
            else -> key
        }
    }

    private fun normalizeIdentifier(raw: String): String {
        return raw.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .uppercase(Locale.ROOT)
            .take(44)
    }

    private fun buildAiCampaignKey(template: TemplateDesc): String {
        val key = if (!template.isCustom) {
            template.key.lowercase(Locale.ROOT)
        } else {
            "custom_${normalizeIdentifier(template.formId).lowercase(Locale.ROOT)}"
        }
        return "ai_agent_$key"
    }

    private fun parseConfig(raw: String): StoredFormConfig {
        if (raw.isBlank()) return StoredFormConfig()
        return runCatching {
            val trimmed = raw.trim()
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
        }.getOrElse {
            StoredFormConfig()
        }
    }

    private fun hasValidSavedContacts(contactSettings: AgentFormContactSettings): Boolean {
        return contactSettings.enabled && contactSettings.contacts.any { sanitizePhone(it.phone).isNotBlank() }
    }

    private fun requiresContactVerification(config: StoredFormConfig): Boolean {
        return config.verification.requireContactVerification ||
            config.fields.any { it.type == FieldType.CONTACT_PICKER }
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

    private fun buildVerificationJson(
        verification: FormVerificationSettings,
        contactSettings: AgentFormContactSettings
    ): JSONObject {
        val contactVerificationEnabled =
            verification.requireContactVerification && hasValidSavedContacts(contactSettings)
        return JSONObject().apply {
            put("googleAuth", verification.requireGoogleAuth)
            put("contacts", contactVerificationEnabled)
            put("location", verification.requireLocationVerification)
        }
    }

    private fun buildContactVerificationJson(
        verification: FormVerificationSettings,
        contactSettings: AgentFormContactSettings
    ): JSONObject {
        val normalizedContacts = contactSettings.contacts.mapNotNull { contact ->
            val normalizedPhone = sanitizePhone(contact.phone)
            if (normalizedPhone.isBlank()) {
                null
            } else {
                val normalizedName = contact.name.trim().ifBlank { normalizedPhone }
                normalizedName to normalizedPhone
            }
        }
        val contactVerificationEnabled =
            verification.requireContactVerification &&
                contactSettings.enabled &&
                normalizedContacts.isNotEmpty()

        val contactsJson = JSONArray()
        normalizedContacts.forEach { (name, phone) ->
            contactsJson.put(
                JSONObject().apply {
                    put("name", name)
                    put("phone", phone)
                }
            )
        }

        return JSONObject().apply {
            put("enabled", contactVerificationEnabled)
            put("requiredMatch", contactVerificationEnabled)
            put("contacts", contactsJson)
        }
    }

    private fun buildPostSubmitContentJson(contactSettings: AgentFormContactSettings): JSONObject {
        val normalizedVideoUrl = contactSettings.postSubmitVideoUrl.trim()
        val normalizedPdfs = contactSettings.postSubmitPdfs.filter { it.url.trim().isNotBlank() && it.name.trim().isNotBlank() }
        val enabled = contactSettings.postSubmitContentEnabled && (normalizedVideoUrl.isNotBlank() || normalizedPdfs.isNotEmpty())

        val pdfsJson = JSONArray()
        normalizedPdfs.forEach { pdf ->
            pdfsJson.put(
                JSONObject().apply {
                    put("id", pdf.id)
                    put("name", pdf.name.trim())
                    put("url", pdf.url.trim())
                    put("sizeBytes", pdf.sizeBytes)
                }
            )
        }

        return JSONObject().apply {
            put("enabled", enabled)
            put("videoUrl", normalizedVideoUrl)
            put("pdfs", pdfsJson)
        }
    }

    private suspend fun resolveOwnerPhone(uid: String): String {
        val prefs = UserDetailsPreferences(context)
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

    private fun toCloudType(type: FieldType): String = when (type) {
        FieldType.TEXT -> "text"
        FieldType.NUMBER -> "number"
        FieldType.PHONE -> "mobile"
        FieldType.EMAIL -> "email"
        FieldType.CONTACT_PICKER -> "contacts"
        FieldType.LOCATION -> "location"
        FieldType.MEDIA -> "media"
        FieldType.DATE -> "date"
        FieldType.TIME -> "time"
        FieldType.SELECT -> "select"
        FieldType.GOOGLE_AUTH -> "google_auth"
    }

    private fun resolveVerificationStatus(data: Map<String, Any?>): String {
        val direct = data["verificationStatus"]?.toString()?.trim().orEmpty()
        if (direct.isNotBlank()) return direct

        val submission = data["submissionStatus"]?.toString()?.trim().orEmpty()
        if (submission.isNotBlank()) return submission

        val contactStatus = data["contact_verification_status"]?.toString()?.trim().orEmpty()
        if (contactStatus.isNotBlank()) return contactStatus

        val metadata = (data["metadata"] as? Map<*, *>).orEmpty()
        val metadataContact = metadata["contact_verification_status"]?.toString()?.trim().orEmpty()
        if (metadataContact.isNotBlank()) return metadataContact

        return "pending"
    }

    private fun isVerifiedStatus(statusRaw: String): Boolean {
        val status = statusRaw.trim().lowercase(Locale.ROOT)
        return status in setOf("saved_verified", "saved", "verified", "matched", "success")
    }

    private fun buildSummary(data: Map<String, Any?>): String {
        val ignore = setOf(
            "uid", "phone", "campaign", "form_id", "formId", "targetPhone", "target_phone",
            "eventType", "submissionStatus", "verificationStatus", "verificationMessage",
            "timestamp", "metadata", "google_user", "device_info"
        )

        val pairs = data.entries
            .filter { !ignore.contains(it.key) }
            .mapNotNull { entry ->
                val value = entry.value?.toString()?.trim().orEmpty()
                if (value.isBlank() || value.length > 120) null else "${entry.key}=$value"
            }
            .take(5)

        return pairs.joinToString(", ")
    }

    private fun parseTimestamp(value: Any?): Long = when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: parseIso(value)
        else -> System.currentTimeMillis()
    }

    private fun parseIso(value: String): Long {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            parser.parse(value)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun buildLink(formId: String, uid: String, ownerPhone: String, campaign: String, targetPhone: String): String {
        val builder = Uri.Builder()
            .scheme("https")
            .authority("chataiform.com")
            .appendPath("f")
            .appendPath(formId)
            .appendQueryParameter("u", uid)
            .appendQueryParameter("p", ownerPhone)
            .appendQueryParameter("c", campaign)
        if (targetPhone.isNotBlank()) builder.appendQueryParameter("t", targetPhone)
        return builder.build().toString()
    }

    private fun sanitizePhone(value: String?): String = value.orEmpty().replace(Regex("[^0-9]"), "")

    private fun saveLastLink(recipientPhone: String, link: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_LINK_PREFIX$recipientPhone", link)
            .apply()
    }

    private fun getLastLink(recipientPhone: String): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_LINK_PREFIX$recipientPhone", "")
            .orEmpty()
    }

    private fun formatTime(ts: Long): String {
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ts))
    }
}
