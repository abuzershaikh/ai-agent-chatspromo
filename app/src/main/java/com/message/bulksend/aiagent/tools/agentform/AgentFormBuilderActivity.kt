package com.message.bulksend.aiagent.tools.agentform

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormContactSettings
import com.message.bulksend.aiagent.tools.agentform.models.FieldType
import com.message.bulksend.aiagent.tools.agentform.models.FormField
import com.message.bulksend.aiagent.tools.agentform.models.FormVerificationSettings
import com.message.bulksend.aiagent.tools.agentform.models.StoredFormConfig
import com.message.bulksend.aiagent.tools.agentform.screens.FormBuilderScreen
import com.message.bulksend.db.AgentFormEntity
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.userdetails.UserDetailsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AgentFormBuilderActivity : ComponentActivity() {
    companion object {
        private const val TAG = "AgentForm"
    }

    private data class UserContext(val uid: String, val phone: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val formId = intent.getStringExtra("FORM_ID")

        setContent {
            val scope = rememberCoroutineScope()
            var initialForm by remember { mutableStateOf<AgentFormEntity?>(null) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(formId) {
                if (formId != null) {
                    initialForm =
                            AppDatabase.Companion.getInstance(this@AgentFormBuilderActivity)
                                    .agentFormDao()
                                    .getFormById(formId)
                }
                isLoading = false
            }

            MaterialTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    if (!isLoading) {
                        FormBuilderScreen(
                                initialForm = initialForm,
                                onSave = { title, description, fields, verification ->
                                    scope.launch {
                                        Log.d(TAG, "Save button clicked")
                                        isLoading = true

                                        try {
                                            val dao =
                                                    AppDatabase.getInstance(
                                                                    this@AgentFormBuilderActivity
                                                            )
                                                            .agentFormDao()
                                            val normalizedTitle =
                                                    title.trim().ifBlank { "New Agent Form" }
                                            val normalizedDescription = description.trim()
                                            val localId =
                                                    initialForm?.formId ?: UUID.randomUUID().toString()
                                            val localForm =
                                                    AgentFormEntity(
                                                            formId = localId,
                                                            title = normalizedTitle,
                                                            description = normalizedDescription,
                                                            fieldsJson =
                                                                    serializeStoredConfig(
                                                                            fields = fields,
                                                                            verification =
                                                                                    verification
                                                                    ),
                                                            createdAt =
                                                                    initialForm?.createdAt
                                                                            ?: System.currentTimeMillis()
                                                    )

                                            // Save locally first so offline or partial auth states don't
                                            // block form creation.
                                            dao.insertForm(localForm)

                                            val userContext = resolveUserContext()
                                            var syncSucceeded = false
                                            val contactSettings =
                                                    AgentFormContactSettingsManager(
                                                                    this@AgentFormBuilderActivity
                                                            )
                                                            .getSettings()

                                            if (userContext.uid.isNotBlank() &&
                                                            userContext.phone.isNotBlank()
                                            ) {
                                                Log.d(
                                                        TAG,
                                                        "Starting sync for UID=${userContext.uid}, phone=${userContext.phone}"
                                                )
                                                val cloudFormId =
                                                        syncFormToCloud(
                                                                uid = userContext.uid,
                                                                phone = userContext.phone,
                                                                title = normalizedTitle,
                                                                description = normalizedDescription,
                                                                fields = fields,
                                                                verification = verification,
                                                                contactSettings = contactSettings
                                                        )

                                                if (!cloudFormId.isNullOrBlank()) {
                                                    syncSucceeded = true
                                                    if (cloudFormId != localId) {
                                                        dao.insertForm(
                                                                localForm.copy(formId = cloudFormId)
                                                        )
                                                        dao.deleteForm(localForm)
                                                    }
                                                } else {
                                                    Log.e(TAG, "Cloud sync failed for local form $localId")
                                                }
                                            } else {
                                                Log.w(
                                                        TAG,
                                                        "Skipping sync: missing UID/phone (uid='${userContext.uid}', phone='${userContext.phone}')"
                                                )
                                            }

                                            val toastMessage =
                                                    when {
                                                        syncSucceeded -> "Form Saved & Synced!"
                                                        userContext.uid.isBlank() ||
                                                                userContext.phone.isBlank() ->
                                                                "Form saved locally. Login details required for online link."
                                                        else ->
                                                                "Form saved locally. Cloud sync failed, try again later."
                                                    }
                                            Toast.makeText(
                                                            this@AgentFormBuilderActivity,
                                                            toastMessage,
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            finish()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Save Error", e)
                                            Toast.makeText(
                                                            this@AgentFormBuilderActivity,
                                                            "Failed to save form: ${e.message ?: "Unknown error"}",
                                                            Toast.LENGTH_LONG
                                                    )
                                                    .show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    private suspend fun syncFormToCloud(
            uid: String,
            phone: String,
            title: String,
            description: String,
            fields: List<FormField>,
            verification: FormVerificationSettings,
            contactSettings: AgentFormContactSettings
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()

                val jsonBody =
                        JSONObject().apply {
                            put("uid", uid)
                            put("phone", phone)
                            put("title", title)
                            put("description", description)
                            put("fields", buildCloudFields(fields))
                            put("verification", buildVerificationJson(verification, contactSettings))
                            put(
                                    "contactVerification",
                                    buildContactVerificationJson(verification, contactSettings)
                            )
                            put("postSubmitContent", buildPostSubmitContentJson(verification, contactSettings))
                            put("campaign", "android_builder")
                        }

                val request =
                        Request.Builder()
                                .url("https://chataiform.com/api/create-form")
                                .post(jsonBody.toString().toRequestBody(mediaType))
                                .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val responseJson = JSONObject(responseBody ?: "{}")
                    val formId = responseJson.optString("formId")
                    if (formId.isNotBlank()) formId else null
                } else {
                    Log.e(
                            TAG,
                            "Sync Failed: ${response.code} - ${response.body?.string()}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync Error", e)
                null
            }
        }
    }

    private suspend fun resolveUserContext(): UserContext {
        val prefs = UserDetailsPreferences(this@AgentFormBuilderActivity)
        var phone = sanitizePhone(prefs.getPhoneNumber())
        var uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        if (phone.isEmpty()) {
            phone = sanitizePhone(intent.getStringExtra("PHONE"))
            if (phone.isNotEmpty()) {
                Log.d(TAG, "Phone retrieved from Intent")
            }
        }

        if (uid.isEmpty()) {
            uid = intent.getStringExtra("UID").orEmpty()
            if (uid.isNotEmpty()) {
                Log.d(TAG, "UID retrieved from Intent")
            }
        }

        if (phone.isEmpty() && uid.isNotEmpty()) {
            Log.d(TAG, "Phone missing locally. Fetching from Firestore for UID: $uid")
            val firestorePhone = sanitizePhone(fetchPhoneFromFirestore(uid))
            if (firestorePhone.isNotEmpty()) {
                phone = firestorePhone
                prefs.updatePhoneNumber(phone)
                Log.d(TAG, "Phone retrieved from Firestore")
            }
        }

        return UserContext(uid = uid.trim(), phone = phone)
    }

    private fun sanitizePhone(value: String?): String {
        return value.orEmpty().replace(Regex("[^0-9]"), "")
    }

    private fun buildCloudFields(fields: List<FormField>): JSONArray {
        val jsonArray = JSONArray()
        fields.forEach { field ->
            val fieldJson =
                    JSONObject().apply {
                        put("id", field.id)
                        put("type", toCloudFieldType(field.type))
                        put("label", field.label)
                        put("required", field.required)
                        if (field.options.isNotEmpty()) {
                            val optionsJson = JSONArray()
                            field.options.forEach { option -> optionsJson.put(option) }
                            put("options", optionsJson)
                        }
                        if (field.hint.isNotBlank()) {
                            put("hint", field.hint)
                        }
                    }
            jsonArray.put(fieldJson)
        }
        return jsonArray
    }

    private fun buildVerificationJson(
            verification: FormVerificationSettings,
            contactSettings: AgentFormContactSettings
    ): JSONObject {
        val hasSavedContacts = contactSettings.contacts.any { sanitizePhone(it.phone).isNotBlank() }
        val contactVerificationEnabled =
                verification.requireContactVerification &&
                        contactSettings.enabled &&
                        hasSavedContacts
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
        val normalizedContacts =
                contactSettings.contacts.mapNotNull { contact ->
                    val normalizedName = contact.name.trim()
                    val normalizedPhone = sanitizePhone(contact.phone)
                    if (normalizedPhone.isBlank()) null
                    else (normalizedName.ifBlank { normalizedPhone } to normalizedPhone)
                }

        val contactsJson = JSONArray()
        normalizedContacts.forEach { (name, phone) ->
            contactsJson.put(
                    JSONObject().apply {
                        put("name", name)
                        put("phone", phone)
                    }
            )
        }

        val contactVerificationEnabled =
                verification.requireContactVerification &&
                        contactSettings.enabled &&
                        normalizedContacts.isNotEmpty()

        return JSONObject().apply {
            put("enabled", contactVerificationEnabled)
            put("requiredMatch", contactVerificationEnabled)
            put("contacts", contactsJson)
        }
    }

    private fun buildPostSubmitContentJson(
            verification: FormVerificationSettings,
            contactSettings: AgentFormContactSettings
    ): JSONObject {
        if (!verification.requireContactVerification) {
            return JSONObject().apply {
                put("enabled", false)
                put("videoUrl", "")
                put("pdfs", JSONArray())
            }
        }
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

    private fun serializeStoredConfig(
            fields: List<FormField>,
            verification: FormVerificationSettings
    ): String {
        return Gson().toJson(
                StoredFormConfig(
                        fields = fields,
                        verification = verification
                )
        )
    }

    private fun toCloudFieldType(type: FieldType): String {
        return when (type) {
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
    }

    private suspend fun fetchPhoneFromFirestore(uid: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val document =
                        FirebaseFirestore.getInstance()
                                .collection("userDetails")
                                .document(uid)
                                .get()
                                .await()

                if (document.exists()) {
                    document.getString("phoneNumber")
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore Fetch Error", e)
                null
            }
        }
    }
}
