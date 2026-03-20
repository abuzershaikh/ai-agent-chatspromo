package com.message.bulksend.aiagent.tools.agentform

import android.content.Context
import com.google.gson.Gson
import com.message.bulksend.aiagent.tools.agentform.models.FieldType
import com.message.bulksend.aiagent.tools.agentform.models.FormField
import com.message.bulksend.aiagent.tools.agentform.models.FormVerificationSettings
import com.message.bulksend.aiagent.tools.agentform.models.StoredFormConfig
import com.message.bulksend.db.AgentFormEntity
import com.message.bulksend.db.AppDatabase

object AgentFormPredefinedTemplates {
    private const val CANONICAL_CONTACT_VERIFY_FORM_ID = "preset-contact-save-verification"

    suspend fun seedIfNeeded(context: Context) {
        val dao = AppDatabase.getInstance(context).agentFormDao()
        cleanupLegacyContactVerifyTemplates(dao)
        val templates = buildTemplates()

        templates.forEach { template ->
            val existing = dao.getFormById(template.formId)
            if (existing == null) {
                dao.insertForm(template)
            } else if (
                    existing.title != template.title ||
                            existing.description != template.description ||
                            existing.fieldsJson != template.fieldsJson
            ) {
                dao.insertForm(template.copy(createdAt = existing.createdAt))
            }
        }
    }

    private suspend fun cleanupLegacyContactVerifyTemplates(dao: com.message.bulksend.db.AgentFormDao) {
        val allForms = dao.getAllFormsOnce()
        allForms.forEach { form ->
            if (form.formId == CANONICAL_CONTACT_VERIFY_FORM_ID) {
                return@forEach
            }

            val raw = form.fieldsJson.lowercase()
            val hasLegacyContactFields =
                    raw.contains("\"applicant_name\"") || raw.contains("\"applicant_phone\"")
            val looksLikeContactVerifyTitle = form.title.trim().lowercase().contains("contact save verification")

            if (hasLegacyContactFields || looksLikeContactVerifyTitle) {
                dao.deleteForm(form)
            }
        }
    }

    private fun buildTemplates(): List<AgentFormEntity> {
        val baseTs = System.currentTimeMillis()
        return listOf(
                buildTemplate(
                        formId = "preset-google-signin-email",
                        title = "Verified Google Email Capture",
                        description =
                                "Collect user details with mandatory Google sign-in and verified email.",
                        fields =
                                listOf(
                                        googleAuthField(
                                                id = "google_auth_required",
                                                label = "Google Sign-In",
                                                hint = "Sign in with Google to continue."
                                        ),
                                        textField(
                                                id = "full_name",
                                                label = "Full Name",
                                                required = true,
                                                hint = "Enter your full name."
                                        ),
                                        emailField(
                                                id = "email_id",
                                                label = "Gmail Address",
                                                required = true,
                                                hint = "Use your active Gmail account."
                                        ),
                                        phoneField(
                                                id = "mobile_number",
                                                label = "Mobile Number",
                                                required = false,
                                                hint = "Optional backup contact number."
                                        )
                                ),
                        verification =
                                FormVerificationSettings(
                                        requireGoogleAuth = true
                                ),
                        createdAt = baseTs + 5000
                ),
                buildTemplate(
                        formId = "preset-address-verification",
                        title = "Address Verification (Structured)",
                        description = "Collect complete postal address with contact details.",
                        fields =
                                listOf(
                                        textField(
                                                id = "full_name",
                                                label = "Full Name",
                                                required = true,
                                                hint = "Name as per address proof."
                                        ),
                                        phoneField(
                                                id = "mobile_number",
                                                label = "Mobile Number",
                                                required = true,
                                                hint = "10-digit mobile number."
                                        ),
                                        textField(
                                                id = "address_line_1",
                                                label = "Address Line 1",
                                                required = true,
                                                hint = "House/Flat/Street details."
                                        ),
                                        textField(
                                                id = "address_line_2",
                                                label = "Address Line 2",
                                                required = false,
                                                hint = "Area/Locality (optional)."
                                        ),
                                        textField(
                                                id = "landmark",
                                                label = "Landmark",
                                                required = false,
                                                hint = "Nearby known place."
                                        ),
                                        textField(
                                                id = "city",
                                                label = "City",
                                                required = true,
                                                hint = "City/Town name."
                                        ),
                                        textField(
                                                id = "district",
                                                label = "District",
                                                required = false,
                                                hint = "District (if applicable)."
                                        ),
                                        textField(
                                                id = "state",
                                                label = "State",
                                                required = true,
                                                hint = "State name."
                                        ),
                                        numberField(
                                                id = "pincode",
                                                label = "Pincode",
                                                required = true,
                                                hint = "6-digit postal code."
                                        ),
                                        textField(
                                                id = "country",
                                                label = "Country",
                                                required = true,
                                                hint = "Country name."
                                        )
                                ),
                        verification = FormVerificationSettings(),
                        createdAt = baseTs + 4000
                ),
                buildTemplate(
                        formId = "preset-address-maps-fillup",
                        title = "Address + Live Location Verification",
                        description =
                                "Capture live location and collect supporting address details.",
                        fields =
                                listOf(
                                        textField(
                                                id = "full_name",
                                                label = "Full Name",
                                                required = true,
                                                hint = "Name of respondent."
                                        ),
                                        phoneField(
                                                id = "mobile_number",
                                                label = "Mobile Number",
                                                required = true,
                                                hint = "Primary contact number."
                                        ),
                                        locationField(
                                                id = "address_live_location",
                                                label = "Live Location Verify",
                                                hint = "Capture current GPS location."
                                        ),
                                        textField(
                                                id = "nearby_landmark",
                                                label = "Nearby Landmark",
                                                required = true,
                                                hint = "Major landmark near the address."
                                        ),
                                        textField(
                                                id = "address_line_1",
                                                label = "Address Line 1",
                                                required = true,
                                                hint = "House/Flat/Street details."
                                        ),
                                        textField(
                                                id = "address_line_2",
                                                label = "Address Line 2",
                                                required = false,
                                                hint = "Area/Locality (optional)."
                                        ),
                                        textField(
                                                id = "city",
                                                label = "City",
                                                required = true,
                                                hint = "City/Town name."
                                        ),
                                        textField(
                                                id = "state",
                                                label = "State",
                                                required = true,
                                                hint = "State name."
                                        ),
                                        numberField(
                                                id = "pincode",
                                                label = "Pincode",
                                                required = false,
                                                hint = "Postal code (optional)."
                                        ),
                                        textField(
                                                id = "country",
                                                label = "Country",
                                                required = false,
                                                hint = "Country name."
                                        )
                                ),
                        verification =
                                FormVerificationSettings(
                                        requireLocationVerification = true
                                ),
                        createdAt = baseTs + 3000
                ),
                buildTemplate(
                        formId = "preset-contact-save-verification",
                        title = "Contact Save Verification",
                        description =
                                "Verify user contact from saved phone contacts using VCF + contact picker.",
                        fields =
                                listOf(
                                        contactPickerField(
                                                id = "contact_verify_picker",
                                                label = "Contact Verification",
                                                hint = "Save downloaded VCF, then select contact to verify."
                                        )
                                ),
                        verification =
                                FormVerificationSettings(
                                        requireContactVerification = true
                                ),
                        createdAt = baseTs + 2000
                ),
                buildTemplate(
                        formId = "preset-basic-profile-email",
                        title = "Basic Profile Collection",
                        description = "Collect user profile details with email capture.",
                        fields =
                                listOf(
                                        textField(
                                                id = "full_name",
                                                label = "Full Name",
                                                required = true,
                                                hint = "Enter your full legal name."
                                        ),
                                        emailField(
                                                id = "email_id",
                                                label = "Email ID",
                                                required = true,
                                                hint = "Enter a valid email address."
                                        ),
                                        phoneField(
                                                id = "mobile_number",
                                                label = "Mobile Number",
                                                required = false,
                                                hint = "Optional contact number."
                                        ),
                                        dateField(
                                                id = "date_of_birth",
                                                label = "Date of Birth",
                                                required = false,
                                                hint = "Optional."
                                        )
                                ),
                        verification = FormVerificationSettings(),
                        createdAt = baseTs + 1000
                )
        )
    }

    private fun textField(
            id: String,
            label: String,
            required: Boolean,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.TEXT,
            label = label,
            required = required,
            hint = hint
    )

    private fun numberField(
            id: String,
            label: String,
            required: Boolean,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.NUMBER,
            label = label,
            required = required,
            hint = hint
    )

    private fun phoneField(
            id: String,
            label: String,
            required: Boolean,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.PHONE,
            label = label,
            required = required,
            hint = hint
    )

    private fun emailField(
            id: String,
            label: String,
            required: Boolean,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.EMAIL,
            label = label,
            required = required,
            hint = hint
    )

    private fun dateField(
            id: String,
            label: String,
            required: Boolean,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.DATE,
            label = label,
            required = required,
            hint = hint
    )

    private fun googleAuthField(
            id: String,
            label: String,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.GOOGLE_AUTH,
            label = label,
            required = true,
            hint = hint
    )

    private fun contactPickerField(
            id: String,
            label: String,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.CONTACT_PICKER,
            label = label,
            required = true,
            hint = hint
    )

    private fun locationField(
            id: String,
            label: String,
            hint: String = ""
    ): FormField = FormField(
            id = id,
            type = FieldType.LOCATION,
            label = label,
            required = true,
            hint = hint
    )

    private fun buildTemplate(
            formId: String,
            title: String,
            description: String,
            fields: List<FormField>,
            verification: FormVerificationSettings,
            createdAt: Long
    ): AgentFormEntity {
        val fieldsJson =
                Gson().toJson(
                        StoredFormConfig(
                                fields = fields,
                                verification = verification
                        )
                )
        return AgentFormEntity(
                formId = formId,
                title = title,
                description = description,
                fieldsJson = fieldsJson,
                createdAt = createdAt
        )
    }
}
