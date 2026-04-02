package com.message.bulksend.bulksenderaiagent

enum class ChatLanguage {
    ENGLISH,
    HINDI
}

enum class MessageType {
    TEXT_USER,
    TEXT_BOT,
    CARD_ADD_CONTACT,
    CHIPS_CONTACT_OPTIONS,
    CARD_GROUP_LIST,
    CARD_CAMPAIGN_TYPES,
    CARD_ATTACHMENT_REQUEST,
    CARD_PERMISSION_CHECKLIST,
    CARD_LAUNCH_CAMPAIGN
}

enum class AgentStep {
    INITIALIZING,
    SELECT_CONTACT_SOURCE,
    WAITING_FOR_CONTACTS,
    SELECT_GROUP,
    ENTER_CAMPAIGN_NAME,
    ENTER_MESSAGE,
    SELECT_CAMPAIGN_TYPE,
    PICK_MEDIA,
    PICK_SHEET,
    REVIEW_PERMISSIONS,
    READY_TO_LAUNCH
}

enum class CampaignType(
    val title: String,
    val shortTitle: String,
    val description: String
) {
    TEXT(
        title = "Text Campaign",
        shortTitle = "Text",
        description = "Sirf text message bhejo"
    ),
    CAPTION(
        title = "Caption Campaign",
        shortTitle = "Caption",
        description = "Image, video ya PDF ke saath caption bhejo"
    ),
    TEXT_MEDIA(
        title = "Text + Media",
        shortTitle = "Text + Media",
        description = "Text aur file dono bhejo"
    ),
    SHEET(
        title = "Sheet Campaign",
        shortTitle = "Sheet",
        description = "Excel/CSV data se personalized campaign chalao"
    );

    fun requiresMediaAttachment(): Boolean = this == CAPTION || this == TEXT_MEDIA

    fun requiresSheetFile(): Boolean = this == SHEET

    fun displayTitle(language: ChatLanguage): String {
        return when (language) {
            ChatLanguage.ENGLISH -> title
            ChatLanguage.HINDI -> when (this) {
                TEXT -> "Text Campaign"
                CAPTION -> "Caption Campaign"
                TEXT_MEDIA -> "Text + Media"
                SHEET -> "Sheet Campaign"
            }
        }
    }

    fun displayDescription(language: ChatLanguage): String {
        return when (language) {
            ChatLanguage.ENGLISH -> when (this) {
                TEXT -> "Send text messages only"
                CAPTION -> "Send media with a caption"
                TEXT_MEDIA -> "Send both text and a file"
                SHEET -> "Run a personalized campaign from Excel or CSV"
            }
            ChatLanguage.HINDI -> when (this) {
                TEXT -> "Sirf text message bhejo"
                CAPTION -> "Image, video ya PDF ke saath caption bhejo"
                TEXT_MEDIA -> "Text aur file dono bhejo"
                SHEET -> "Excel ya CSV se personalized campaign chalao"
            }
        }
    }
}

enum class AttachmentKind {
    MEDIA,
    SHEET
}

data class AttachmentRequest(
    val kind: AttachmentKind,
    val title: String,
    val description: String,
    val buttonLabel: String
)

data class PermissionChecklist(
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val hasWhatsApp: Boolean = false,
    val hasWhatsAppBusiness: Boolean = false
) {
    val hasAnyWhatsApp: Boolean
        get() = hasWhatsApp || hasWhatsAppBusiness

    val requiredReady: Boolean
        get() = accessibilityEnabled && overlayEnabled && hasAnyWhatsApp
}

data class CampaignDraft(
    val selectedGroupId: Long? = null,
    val selectedGroupName: String? = null,
    val contactCount: Int = 0,
    val campaignName: String = "",
    val message: String = "",
    val campaignType: CampaignType? = null,
    val mediaUri: String? = null,
    val mediaName: String? = null,
    val sheetUri: String? = null,
    val sheetName: String? = null,
    val countryCode: String = ""
) {
    fun hasGroup(): Boolean = selectedGroupId != null

    fun isSetupComplete(): Boolean {
        val type = campaignType ?: return false
        if (!hasGroup()) return false
        if (campaignName.isBlank() || message.isBlank()) return false
        if (type.requiresMediaAttachment() && mediaUri.isNullOrBlank()) return false
        if (type.requiresSheetFile() && sheetUri.isNullOrBlank()) return false
        return true
    }
}

data class CampaignLaunchRequest(
    val campaignType: CampaignType,
    val groupId: String?,
    val groupName: String,
    val campaignName: String,
    val countryCode: String,
    val message: String,
    val mediaUri: String? = null,
    val sheetUri: String? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "",
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class AgentUiAction {
    object OpenAddContact : AgentUiAction()
    data class PickAttachment(val kind: AttachmentKind) : AgentUiAction()
    object OpenAccessibilitySettings : AgentUiAction()
    object OpenOverlaySettings : AgentUiAction()
    object RequestNotificationPermission : AgentUiAction()
    object LaunchCampaign : AgentUiAction()
}

object AiAgentLaunchExtras {
    const val EXTRA_PRESET_MESSAGE = "AI_AGENT_PRESET_MESSAGE"
    const val EXTRA_PRESET_MEDIA_URI = "AI_AGENT_PRESET_MEDIA_URI"
    const val EXTRA_PRESET_SHEET_URI = "AI_AGENT_PRESET_SHEET_URI"
}
