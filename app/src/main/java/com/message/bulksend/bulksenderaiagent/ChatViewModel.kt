package com.message.bulksend.bulksenderaiagent

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.contactmanager.Group
import com.message.bulksend.overlay.OverlayHelper
import com.message.bulksend.utils.CountryCodeManager
import com.message.bulksend.utils.NotificationPermissionHelper
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import com.message.bulksend.utils.isPackageInstalled
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val contactsRepository = ContactsRepository(appContext)

    private val _language = MutableStateFlow(ChatLanguage.ENGLISH)
    val language: StateFlow<ChatLanguage> = _language.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                text = "I will help you set up your bulk send campaign with a guided native flow. We can handle groups, campaign name, message, campaign type, files, and permissions right here.",
                type = MessageType.TEXT_BOT
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _draft = MutableStateFlow(
        CampaignDraft(
            countryCode = CountryCodeManager.getOrAutoDetectCountry(appContext)?.dial_code
                ?: CountryCodeManager.getSelectedDialCode(appContext)
        )
    )
    val draft: StateFlow<CampaignDraft> = _draft.asStateFlow()

    private val _step = MutableStateFlow(AgentStep.INITIALIZING)
    val step: StateFlow<AgentStep> = _step.asStateFlow()

    private val _attachmentRequest = MutableStateFlow<AttachmentRequest?>(null)
    val attachmentRequest: StateFlow<AttachmentRequest?> = _attachmentRequest.asStateFlow()

    private val _permissionChecklist = MutableStateFlow(PermissionChecklist())
    val permissionChecklist: StateFlow<PermissionChecklist> = _permissionChecklist.asStateFlow()

    private val _uiActions = MutableSharedFlow<AgentUiAction>(extraBufferCapacity = 8)
    val uiActions: SharedFlow<AgentUiAction> = _uiActions.asSharedFlow()

    private var hasShownInitialPrompt = false
    private var hasAnnouncedReady = false

    init {
        observeSavedGroups()
        refreshEnvironment()
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        updateLanguagePreference(trimmed)
        addUserMessage(trimmed)

        if (handleSmartCommands(trimmed)) {
            return
        }

        when (_step.value) {
            AgentStep.SELECT_CONTACT_SOURCE -> handleContactSourceInput(trimmed.lowercase())
            AgentStep.WAITING_FOR_CONTACTS -> handleWaitingForContactsInput(trimmed.lowercase())
            AgentStep.SELECT_GROUP -> handleGroupSelectionInput(trimmed)
            AgentStep.ENTER_CAMPAIGN_NAME -> saveCampaignName(trimmed)
            AgentStep.ENTER_MESSAGE -> saveCampaignMessage(trimmed)
            AgentStep.SELECT_CAMPAIGN_TYPE -> handleCampaignTypeInput(trimmed.lowercase())
            AgentStep.PICK_MEDIA -> {
                addBotMessage(
                    english = "Use the file picker card below to choose an image, video, or PDF, or type 'pick file' and I will open it for you.",
                    hindi = "Niche file picker card se image, video ya PDF choose karo, ya 'pick file' likho aur main open kar dunga."
                )
            }
            AgentStep.PICK_SHEET -> {
                addBotMessage(
                    english = "Use the sheet picker card below to choose an Excel or CSV file, or type 'pick sheet' and I will open it for you.",
                    hindi = "Niche sheet picker card se Excel ya CSV file choose karo, ya 'pick sheet' likho aur main open kar dunga."
                )
            }
            AgentStep.REVIEW_PERMISSIONS -> {
                refreshEnvironment()
                if (!_permissionChecklist.value.requiredReady) {
                    addBotMessage(buildMissingPermissionMessage())
                }
            }
            AgentStep.READY_TO_LAUNCH -> {
                addBotMessage(
                    english = "Everything is ready. Tap the launch card or type 'launch campaign' and I will open the filled screen.",
                    hindi = "Sab ready hai. Launch card tap karo ya 'launch campaign' likho, main filled screen open kar dunga."
                )
            }
            AgentStep.INITIALIZING -> {
                addBotMessage(
                    english = "One moment. I am checking your saved groups and permissions.",
                    hindi = "Ek second, main aapke saved groups aur permissions check kar raha hoon."
                )
            }
        }
    }

    fun onOptionSelected(option: String) {
        handleContactSourceSelection(option = option, echoSelection = true)
    }

    fun onGroupSelected(group: Group) {
        applyGroupSelection(group)
        addUserMessage(group.name)
        addBotMessage(
            english = "`" + group.name + "` is selected.",
            hindi = "`" + group.name + "` select ho gaya."
        )
        promptNextMissingStep()
    }

    fun onCampaignTypeSelected(type: CampaignType) {
        applyCampaignType(type)
        addUserMessage(type.displayTitle(_language.value))
        addBotMessage(
            english = type.displayTitle(_language.value) + " selected.",
            hindi = type.displayTitle(_language.value) + " select ho gaya."
        )
        promptNextMissingStep()
    }

    fun onContactAdded() {
        if (_groups.value.isEmpty()) {
            addBotMessage(
                english = "I am refreshing your groups. If the group was saved, it should appear here in a moment.",
                hindi = "Main groups refresh kar raha hoon. Agar group save ho gaya hai to ek second me yahin dikhega."
            )
            _step.value = AgentStep.WAITING_FOR_CONTACTS
            appendCardOnce(MessageType.CARD_ADD_CONTACT)
            return
        }

        addBotMessage(
            english = "Your group is saved. Now choose the group you want to use.",
            hindi = "Group save ho gaya. Ab jis group ko use karna hai usse select karo."
        )
        appendCardOnce(MessageType.CARD_GROUP_LIST)
        _step.value = AgentStep.SELECT_GROUP
    }

    fun onMediaPicked(uriString: String) {
        val fileName = resolveFileName(Uri.parse(uriString)) ?: localized("Selected file", "Selected file")
        _draft.update {
            it.copy(
                mediaUri = uriString,
                mediaName = fileName
            )
        }
        addUserMessage(fileName)
        addBotMessage(
            english = "The file is attached.",
            hindi = "File attach ho gayi."
        )
        promptNextMissingStep()
    }

    fun onSheetPicked(uriString: String) {
        val fileName = resolveFileName(Uri.parse(uriString)) ?: localized("Selected sheet", "Selected sheet")
        _draft.update {
            it.copy(
                sheetUri = uriString,
                sheetName = fileName
            )
        }
        addUserMessage(fileName)
        addBotMessage(
            english = "Your sheet file is ready.",
            hindi = "Sheet file ready hai."
        )
        promptNextMissingStep()
    }

    fun refreshEnvironment() {
        val status = PermissionChecklist(
            accessibilityEnabled = isAccessibilityServiceEnabled(appContext),
            overlayEnabled = OverlayHelper.hasOverlayPermission(appContext),
            notificationsEnabled = NotificationPermissionHelper.areNotificationsEnabled(appContext),
            hasWhatsApp = isPackageInstalled(appContext, "com.whatsapp"),
            hasWhatsAppBusiness = isPackageInstalled(appContext, "com.whatsapp.w4b")
        )
        _permissionChecklist.value = status

        val currentDraft = _draft.value
        if (!currentDraft.isSetupComplete()) {
            return
        }

        appendCardOnce(MessageType.CARD_PERMISSION_CHECKLIST)

        if (status.requiredReady) {
            _step.value = AgentStep.READY_TO_LAUNCH
            appendCardOnce(MessageType.CARD_LAUNCH_CAMPAIGN)
            if (!hasAnnouncedReady) {
                hasAnnouncedReady = true
                addBotMessage(
                    english = "All required permissions are ready. You can open the filled campaign screen now.",
                    hindi = "Sab required permissions ready hain. Ab aap filled campaign screen open kar sakte ho."
                )
            }
        } else {
            _step.value = AgentStep.REVIEW_PERMISSIONS
            hasAnnouncedReady = false
        }
    }

    fun buildLaunchRequest(): CampaignLaunchRequest? {
        val currentDraft = _draft.value
        val campaignType = currentDraft.campaignType ?: return null
        if (!currentDraft.isSetupComplete()) return null

        return CampaignLaunchRequest(
            campaignType = campaignType,
            groupId = currentDraft.selectedGroupId?.toString(),
            groupName = currentDraft.selectedGroupName.orEmpty(),
            campaignName = currentDraft.campaignName,
            countryCode = currentDraft.countryCode,
            message = currentDraft.message,
            mediaUri = currentDraft.mediaUri,
            sheetUri = currentDraft.sheetUri
        )
    }

    private fun observeSavedGroups() {
        viewModelScope.launch {
            contactsRepository.loadGroups().collect { savedGroups ->
                val filteredGroups = savedGroups
                    .filter { !it.name.contains("/") }
                    .sortedByDescending { it.timestamp }

                _groups.value = filteredGroups

                if (!hasShownInitialPrompt) {
                    hasShownInitialPrompt = true
                    if (filteredGroups.isEmpty()) {
                        addBotMessage(
                            english = "I could not find any saved group yet. Use Add Contact Now below to create your first group.",
                            hindi = "Abhi koi saved group nahi mila. Neeche Add Contact Now se pehla group bana lo."
                        )
                        appendCardOnce(MessageType.CARD_ADD_CONTACT)
                        _step.value = AgentStep.WAITING_FOR_CONTACTS
                    } else {
                        addBotMessage(
                            english = "I found ${filteredGroups.size} saved groups. Do you want to use an existing group or create a new one?",
                            hindi = "Maine ${filteredGroups.size} saved groups fetch kar liye. Purana group use karna hai ya naya banana hai?"
                        )
                        appendCardOnce(MessageType.CHIPS_CONTACT_OPTIONS)
                        _step.value = AgentStep.SELECT_CONTACT_SOURCE
                    }
                    return@collect
                }

                if (_step.value == AgentStep.WAITING_FOR_CONTACTS && filteredGroups.isNotEmpty() && !_draft.value.hasGroup()) {
                    addBotMessage(
                        english = "Your new group is visible now. Select it to continue.",
                        hindi = "Naya group list me aa gaya. Ab usse select karo."
                    )
                    appendCardOnce(MessageType.CARD_GROUP_LIST)
                    _step.value = AgentStep.SELECT_GROUP
                }
            }
        }
    }

    private fun handleSmartCommands(rawText: String): Boolean {
        val normalized = rawText.lowercase()

        when {
            containsAny(normalized, "reset", "start over", "restart", "clear setup", "dobara", "phir se") -> {
                resetConversation()
                return true
            }
            containsAny(normalized, "summary", "status", "progress", "what is set", "kya set", "setup summary") -> {
                addBotMessage(buildProgressSummary())
                return true
            }
            containsAny(normalized, "help", "what can you do", "guide me", "kaise", "samjha", "madad") -> {
                addBotMessage(buildHelpMessage())
                return true
            }
            containsAny(normalized, "recommend", "suggest", "which type", "what type", "best campaign", "kaunsa type") -> {
                addBotMessage(buildRecommendationMessage(normalized))
                return true
            }
            containsAny(normalized, "difference", "farak", "caption vs", "text media vs") -> {
                addBotMessage(buildTypeDifferenceMessage())
                return true
            }
            containsAny(normalized, "why accessibility", "accessibility kyu", "what is accessibility") -> {
                addBotMessage(buildAccessibilityHelp())
                return true
            }
            containsAny(normalized, "why overlay", "overlay kyu", "what is overlay") -> {
                addBotMessage(buildOverlayHelp())
                return true
            }
            containsAny(normalized, "launch campaign", "open campaign", "start campaign", "open filled screen", "launch now") -> {
                if (_permissionChecklist.value.requiredReady && buildLaunchRequest() != null) {
                    addBotMessage(
                        english = "Opening the filled campaign screen now.",
                        hindi = "Filled campaign screen abhi open kar raha hoon."
                    )
                    emitUiAction(AgentUiAction.LaunchCampaign)
                } else {
                    addBotMessage(
                        english = "We still need to finish the setup before launching. Type 'summary' to see what is missing.",
                        hindi = "Launch se pehle setup complete karna hoga. Kya missing hai dekhne ke liye 'summary' likho."
                    )
                }
                return true
            }
        }

        if (handleDirectUiActions(normalized)) {
            return true
        }

        if (applyInlineUpdates(rawText)) {
            return true
        }

        if (containsAny(normalized, "continue", "next", "go ahead", "aage", "proceed")) {
            if (!_draft.value.hasGroup() && _groups.value.size == 1) {
                applyGroupSelection(_groups.value.first())
                addBotMessage(
                    english = "I selected your only saved group automatically.",
                    hindi = "Maine aapka ek hi saved group automatically select kar diya."
                )
                promptNextMissingStep()
                return true
            }
            addBotMessage(buildProgressSummary())
            return true
        }

        return false
    }

    private fun handleDirectUiActions(normalized: String): Boolean {
        if (containsAny(normalized, "add contact", "open contacts", "contact screen", "open contact list")) {
            addBotMessage(
                english = "Opening the contact screen for you.",
                hindi = "Main contact screen open kar raha hoon."
            )
            emitUiAction(AgentUiAction.OpenAddContact)
            return true
        }

        if (_step.value == AgentStep.PICK_MEDIA && containsAny(normalized, "pick file", "pick media", "browse file", "attach file", "choose file")) {
            addBotMessage(
                english = "Opening the file picker.",
                hindi = "Main file picker open kar raha hoon."
            )
            emitUiAction(AgentUiAction.PickAttachment(AttachmentKind.MEDIA))
            return true
        }

        if (_step.value == AgentStep.PICK_SHEET && containsAny(normalized, "pick sheet", "browse sheet", "choose sheet", "attach sheet", "open sheet")) {
            addBotMessage(
                english = "Opening the sheet picker.",
                hindi = "Main sheet picker open kar raha hoon."
            )
            emitUiAction(AgentUiAction.PickAttachment(AttachmentKind.SHEET))
            return true
        }

        if (containsAny(normalized, "open accessibility", "enable accessibility", "accessibility settings")) {
            emitUiAction(AgentUiAction.OpenAccessibilitySettings)
            return true
        }

        if (containsAny(normalized, "open overlay", "enable overlay", "overlay settings")) {
            emitUiAction(AgentUiAction.OpenOverlaySettings)
            return true
        }

        if (containsAny(normalized, "open notification", "allow notification", "notification settings")) {
            emitUiAction(AgentUiAction.RequestNotificationPermission)
            return true
        }

        return false
    }

    private fun applyInlineUpdates(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        val updates = mutableListOf<String>()
        var changed = false

        val shouldDetectGroup = _step.value == AgentStep.SELECT_GROUP || containsAny(normalized, "group", "use", "select", "choose")
        if (shouldDetectGroup) {
            findBestGroupFromText(rawText)?.let { group ->
                if (_draft.value.selectedGroupId != group.id) {
                    applyGroupSelection(group)
                    updates += localized("Group: ${group.name}", "Group: ${group.name}")
                    changed = true
                }
            }
        }

        extractCampaignName(rawText)?.let { name ->
            if (name.isNotBlank() && name != _draft.value.campaignName) {
                _draft.update { it.copy(campaignName = name) }
                updates += localized("Campaign name: $name", "Campaign name: $name")
                changed = true
            }
        }

        extractExplicitMessage(rawText)?.let { message ->
            if (message.isNotBlank() && message != _draft.value.message) {
                _draft.update { it.copy(message = message) }
                appendCardOnce(MessageType.CARD_CAMPAIGN_TYPES)
                updates += localized("Message saved", "Message save ho gaya")
                changed = true
            }
        }

        detectExplicitCampaignType(normalized)?.let { type ->
            if (_draft.value.campaignType != type) {
                applyCampaignType(type)
                updates += localized("Type: ${type.displayTitle(_language.value)}", "Type: ${type.displayTitle(_language.value)}")
                changed = true
            }
        }

        if (!changed) {
            return false
        }

        addBotMessage(
            english = "Got it. " + updates.joinToString(" | "),
            hindi = "Theek hai. " + updates.joinToString(" | ")
        )
        promptNextMissingStep()
        return true
    }

    private fun handleContactSourceInput(normalizedText: String) {
        when {
            containsAny(normalizedText, "old", "existing", "purana", "old contacts") -> {
                handleContactSourceSelection(option = "old", echoSelection = false)
            }
            containsAny(normalizedText, "new", "create", "naya", "new group") -> {
                handleContactSourceSelection(option = "new", echoSelection = false)
            }
            else -> {
                addBotMessage(
                    english = "Use one of the buttons: 'Use Existing Group' or 'Create New Group'.",
                    hindi = "Buttons use kar lo: 'Proceed with Old Contacts' ya 'Create New Group'."
                )
            }
        }
    }

    private fun handleContactSourceSelection(option: String, echoSelection: Boolean) {
        if (echoSelection) {
            addUserMessage(
                if (option == "old") {
                    localized("Use Existing Group", "Proceed with Old Contacts")
                } else {
                    localized("Create New Group", "Create New Group")
                }
            )
        }

        if (option == "old") {
            if (_groups.value.isEmpty()) {
                addBotMessage(
                    english = "I still cannot find any saved groups. Please create a new group first.",
                    hindi = "Saved groups abhi nahi mile. Pehle naya group bana lo."
                )
                appendCardOnce(MessageType.CARD_ADD_CONTACT)
                _step.value = AgentStep.WAITING_FOR_CONTACTS
            } else {
                addBotMessage(
                    english = "Here are your saved groups. Select the one you want to use.",
                    hindi = "Yeh rahe saved groups. Jis group ko use karna hai usse select karo."
                )
                appendCardOnce(MessageType.CARD_GROUP_LIST)
                _step.value = AgentStep.SELECT_GROUP
            }
        } else {
            addBotMessage(
                english = "Tap Add Contact Now, save a group, and come back here. I will continue from the next step.",
                hindi = "Add Contact Now par tap karo aur group save karke wapas aao. Main yahin se next step continue karunga."
            )
            appendCardOnce(MessageType.CARD_ADD_CONTACT)
            _step.value = AgentStep.WAITING_FOR_CONTACTS
        }
    }

    private fun handleWaitingForContactsInput(normalizedText: String) {
        if (containsAny(normalizedText, "done", "added", "saved", "save", "ho gaya", "hogaya", "kar diya")) {
            onContactAdded()
        } else {
            addBotMessage(
                english = "Save the group first, then type 'done' or say 'open contacts' and I will take you there.",
                hindi = "Pehle group save karo. Uske baad 'done' likho ya 'open contacts' bolo aur main le jaunga."
            )
        }
    }

    private fun handleGroupSelectionInput(rawText: String) {
        val group = findBestGroupFromText(rawText)
        if (group == null) {
            addBotMessage(
                english = "I could not match that group clearly. It is better to tap the group card below.",
                hindi = "Mujhe exact group match nahi mila. Better hoga ki niche group card par tap kar lo."
            )
            return
        }

        applyGroupSelection(group)
        addBotMessage(
            english = "`" + group.name + "` is selected.",
            hindi = "`" + group.name + "` select ho gaya."
        )
        promptNextMissingStep()
    }

    private fun saveCampaignName(name: String) {
        if (name.isBlank()) {
            addBotMessage(
                english = "The campaign name cannot be blank. Send me a short campaign name.",
                hindi = "Campaign name blank nahi rehna chahiye. Ek short naam bhejo."
            )
            return
        }

        _draft.update { it.copy(campaignName = name) }
        addBotMessage(
            english = "Great. Campaign name saved.",
            hindi = "Badhiya. Campaign name save ho gaya."
        )
        promptNextMissingStep()
    }

    private fun saveCampaignMessage(message: String) {
        if (message.isBlank()) {
            addBotMessage(
                english = "The message cannot be blank. Type the text you want to send.",
                hindi = "Message blank nahi ho sakta. Jo text bhejna hai woh type karo."
            )
            return
        }

        _draft.update { it.copy(message = message) }
        appendCardOnce(MessageType.CARD_CAMPAIGN_TYPES)
        addBotMessage(
            english = "Message saved.",
            hindi = "Message save ho gaya."
        )
        promptNextMissingStep()
    }

    private fun handleCampaignTypeInput(normalizedText: String) {
        val type = detectCampaignTypeFromText(normalizedText)
        if (type == null) {
            addBotMessage(
                english = "Tap one of the campaign type cards below. You can also type 'text campaign', 'caption campaign', 'text and media', or 'sheet campaign'.",
                hindi = "Campaign type card par tap karo. Ya 'text campaign', 'caption campaign', 'text and media', ya 'sheet campaign' likho."
            )
            return
        }

        applyCampaignType(type)
        addBotMessage(
            english = type.displayTitle(_language.value) + " selected.",
            hindi = type.displayTitle(_language.value) + " select ho gaya."
        )
        promptNextMissingStep()
    }

    private fun applyGroupSelection(group: Group) {
        val existingCountryCode = _draft.value.countryCode.ifBlank {
            CountryCodeManager.getOrAutoDetectCountry(appContext)?.dial_code
                ?: CountryCodeManager.getSelectedDialCode(appContext)
        }

        _draft.update {
            it.copy(
                selectedGroupId = group.id,
                selectedGroupName = group.name,
                contactCount = group.contacts.size,
                countryCode = existingCountryCode
            )
        }
        _attachmentRequest.value = null
        hasAnnouncedReady = false
    }

    private fun applyCampaignType(type: CampaignType) {
        val currentDraft = _draft.value
        _draft.value = currentDraft.copy(
            campaignType = type,
            mediaUri = if (type.requiresMediaAttachment()) currentDraft.mediaUri else null,
            mediaName = if (type.requiresMediaAttachment()) currentDraft.mediaName else null,
            sheetUri = if (type.requiresSheetFile()) currentDraft.sheetUri else null,
            sheetName = if (type.requiresSheetFile()) currentDraft.sheetName else null
        )
        hasAnnouncedReady = false
    }

    private fun promptNextMissingStep() {
        val currentDraft = _draft.value

        when {
            !currentDraft.hasGroup() -> {
                if (_groups.value.isEmpty()) {
                    appendCardOnce(MessageType.CARD_ADD_CONTACT)
                    _step.value = AgentStep.WAITING_FOR_CONTACTS
                    addBotMessage(
                        english = "Please create a group first so we can continue.",
                        hindi = "Continue karne ke liye pehle ek group banao."
                    )
                } else {
                    appendCardOnce(MessageType.CARD_GROUP_LIST)
                    _step.value = AgentStep.SELECT_GROUP
                    addBotMessage(
                        english = "Now select the group you want to use.",
                        hindi = "Ab jis group ko use karna hai usse select karo."
                    )
                }
            }
            currentDraft.campaignName.isBlank() -> {
                _step.value = AgentStep.ENTER_CAMPAIGN_NAME
                addBotMessage(
                    english = "What would you like to name this campaign?",
                    hindi = "Ab campaign name kya rakhna hai?"
                )
            }
            currentDraft.message.isBlank() -> {
                _step.value = AgentStep.ENTER_MESSAGE
                addBotMessage(
                    english = "Now send the message you want to use in this campaign.",
                    hindi = "Ab campaign me jo message bhejna hai woh type karo."
                )
            }
            currentDraft.campaignType == null -> {
                appendCardOnce(MessageType.CARD_CAMPAIGN_TYPES)
                _step.value = AgentStep.SELECT_CAMPAIGN_TYPE
                addBotMessage(
                    english = "Choose the campaign type now.",
                    hindi = "Ab campaign type choose karo."
                )
            }
            currentDraft.campaignType.requiresSheetFile() && currentDraft.sheetUri.isNullOrBlank() -> {
                _attachmentRequest.value = AttachmentRequest(
                    kind = AttachmentKind.SHEET,
                    title = localized("Choose a sheet file", "Sheet file choose karo"),
                    description = localized(
                        "Pick an Excel or CSV file so I can prepare the sheet campaign.",
                        "Excel ya CSV file pick karo taaki main sheet campaign ready kar saku."
                    ),
                    buttonLabel = localized("Pick Sheet File", "Pick Sheet File")
                )
                appendCardOnce(MessageType.CARD_ATTACHMENT_REQUEST)
                _step.value = AgentStep.PICK_SHEET
                addBotMessage(
                    english = "Now choose your sheet file.",
                    hindi = "Ab apni sheet file choose karo."
                )
            }
            currentDraft.campaignType.requiresMediaAttachment() && currentDraft.mediaUri.isNullOrBlank() -> {
                _attachmentRequest.value = AttachmentRequest(
                    kind = AttachmentKind.MEDIA,
                    title = localized("Choose an image, video, or PDF", "Image, video ya PDF choose karo"),
                    description = localized(
                        "This campaign needs a file attachment before we continue.",
                        "Is campaign ko continue karne ke liye file attachment zaroori hai."
                    ),
                    buttonLabel = localized("Pick Media or PDF", "Pick Media / PDF")
                )
                appendCardOnce(MessageType.CARD_ATTACHMENT_REQUEST)
                _step.value = AgentStep.PICK_MEDIA
                addBotMessage(
                    english = "Attach the file for this campaign now.",
                    hindi = "Is campaign ke liye ab file attach karo."
                )
            }
            else -> {
                addBotMessage(
                    english = "The setup is complete. I will review permissions now.",
                    hindi = "Setup complete hai. Ab permissions review karte hain."
                )
                moveToPermissionReview()
            }
        }
    }

    private fun moveToPermissionReview() {
        _attachmentRequest.value = null
        appendCardOnce(MessageType.CARD_PERMISSION_CHECKLIST)
        _step.value = AgentStep.REVIEW_PERMISSIONS
        refreshEnvironment()
        if (!_permissionChecklist.value.requiredReady) {
            addBotMessage(buildMissingPermissionMessage())
        }
    }

    private fun buildProgressSummary(): String {
        val currentDraft = _draft.value
        val nextStep = when {
            !currentDraft.hasGroup() -> localized("Select a group", "Group select karo")
            currentDraft.campaignName.isBlank() -> localized("Set the campaign name", "Campaign name set karo")
            currentDraft.message.isBlank() -> localized("Send the campaign message", "Campaign message bhejo")
            currentDraft.campaignType == null -> localized("Choose the campaign type", "Campaign type choose karo")
            currentDraft.campaignType.requiresMediaAttachment() && currentDraft.mediaUri.isNullOrBlank() -> localized("Attach the media file", "Media file attach karo")
            currentDraft.campaignType.requiresSheetFile() && currentDraft.sheetUri.isNullOrBlank() -> localized("Attach the sheet file", "Sheet file attach karo")
            !_permissionChecklist.value.requiredReady -> localized("Review permissions", "Permissions review karo")
            else -> localized("Launch the campaign", "Campaign launch karo")
        }

        val typeTitle = currentDraft.campaignType?.displayTitle(_language.value) ?: localized("Not selected", "Select nahi hua")
        val mediaLabel = currentDraft.mediaName ?: localized("Not attached", "Attach nahi hua")
        val sheetLabel = currentDraft.sheetName ?: localized("Not attached", "Attach nahi hua")

        return localized(
            english = buildString {
                appendLine("Current setup:")
                appendLine("Group: ${currentDraft.selectedGroupName ?: "Not selected"}")
                appendLine("Campaign name: ${currentDraft.campaignName.ifBlank { "Not set" }}")
                appendLine("Message: ${if (currentDraft.message.isBlank()) "Not set" else "Ready"}")
                appendLine("Type: $typeTitle")
                appendLine("Media: $mediaLabel")
                appendLine("Sheet: $sheetLabel")
                append("Next: $nextStep")
            },
            hindi = buildString {
                appendLine("Current setup:")
                appendLine("Group: ${currentDraft.selectedGroupName ?: "Select nahi hua"}")
                appendLine("Campaign name: ${currentDraft.campaignName.ifBlank { "Set nahi hua" }}")
                appendLine("Message: ${if (currentDraft.message.isBlank()) "Set nahi hua" else "Ready"}")
                appendLine("Type: $typeTitle")
                appendLine("Media: $mediaLabel")
                appendLine("Sheet: $sheetLabel")
                append("Next: $nextStep")
            }
        )
    }

    private fun buildHelpMessage(): String {
        return localized(
            english = "You can talk to me naturally. Examples: 'use marketing group', 'campaign name is Summer Sale', 'message: Hello #name#', 'caption campaign', 'pick file', 'summary', 'recommend a type', 'launch campaign', or 'reset'.",
            hindi = "Aap naturally likh sakte ho. Example: 'marketing group use karo', 'campaign name Summer Sale', 'message: Hello #name#', 'caption campaign', 'pick file', 'summary', 'recommend a type', 'launch campaign', ya 'reset'."
        )
    }

    private fun buildRecommendationMessage(normalized: String): String {
        val currentDraft = _draft.value
        val recommendation = when {
            currentDraft.sheetUri != null || containsAny(normalized, "sheet", "excel", "csv") -> CampaignType.SHEET
            currentDraft.mediaUri != null && currentDraft.message.isNotBlank() -> CampaignType.CAPTION
            currentDraft.mediaUri != null -> CampaignType.CAPTION
            containsAny(normalized, "separate", "alag", "text and media") -> CampaignType.TEXT_MEDIA
            else -> CampaignType.TEXT
        }

        return when (recommendation) {
            CampaignType.TEXT -> localized(
                "I recommend Text Campaign because it is the simplest option when you only need to send a text message.",
                "Main Text Campaign recommend karta hoon kyunki jab sirf text bhejna ho to yeh sabse simple option hai."
            )
            CampaignType.CAPTION -> localized(
                "I recommend Caption Campaign because your file and caption can be sent together in one media message.",
                "Main Caption Campaign recommend karta hoon kyunki file aur caption ek hi media message me bheje ja sakte hain."
            )
            CampaignType.TEXT_MEDIA -> localized(
                "I recommend Text + Media when you want the text and the file to go as separate messages.",
                "Main Text + Media recommend karta hoon jab aap text aur file ko alag-alag messages me bhejna chahte ho."
            )
            CampaignType.SHEET -> localized(
                "I recommend Sheet Campaign because your Excel or CSV data can personalize each message automatically.",
                "Main Sheet Campaign recommend karta hoon kyunki Excel ya CSV data se har message automatically personalize ho jayega."
            )
        }
    }

    private fun buildTypeDifferenceMessage(): String {
        return localized(
            english = "Caption Campaign sends the file and caption together in one media message. Text + Media sends the text first and then sends the file separately. Use Caption for a single bundled send, and Text + Media when you want two separate messages.",
            hindi = "Caption Campaign me file aur caption ek hi media message me jata hai. Text + Media me pehle text jata hai aur uske baad file alag se bheji jati hai. Ek bundled send chahiye to Caption, aur alag-alag do messages chahiye to Text + Media use karo."
        )
    }

    private fun buildAccessibilityHelp(): String {
        return localized(
            english = "Accessibility Service lets the app automate the send action inside WhatsApp. Without it, bulk send cannot tap the send button for you.",
            hindi = "Accessibility Service app ko WhatsApp ke andar send action automate karne deta hai. Iske bina bulk send aapke liye send button tap nahi kar sakta."
        )
    }

    private fun buildOverlayHelp(): String {
        return localized(
            english = "Overlay permission is used for the floating campaign controls and live progress while your campaign is running.",
            hindi = "Overlay permission floating campaign controls aur live progress dikhane ke liye use hoti hai jab aapka campaign chal raha hota hai."
        )
    }

    private fun buildMissingPermissionMessage(): String {
        val status = _permissionChecklist.value
        val pending = mutableListOf<String>()
        if (!status.accessibilityEnabled) pending += localized("Accessibility Service", "Accessibility Service")
        if (!status.overlayEnabled) pending += localized("Overlay Permission", "Overlay Permission")
        if (!status.notificationsEnabled) pending += localized("Notifications", "Notifications")
        if (!status.hasAnyWhatsApp) pending += localized("WhatsApp installation", "WhatsApp installation")

        return if (pending.isEmpty()) {
            localized(
                english = "Everything looks ready. Refresh once and use the launch card.",
                hindi = "Sab ready dikh raha hai. Ek baar refresh karke launch card use karo."
            )
        } else {
            localized(
                english = "These items are still pending: ${pending.joinToString(", ")}. Use the buttons below, or type commands like 'open accessibility', 'open overlay', or 'allow notifications'.",
                hindi = "Abhi yeh pending hai: ${pending.joinToString(", ")}. Neeche buttons use karo, ya 'open accessibility', 'open overlay', ya 'allow notifications' likho."
            )
        }
    }

    private fun resetConversation() {
        hasAnnouncedReady = false
        _attachmentRequest.value = null
        _draft.value = CampaignDraft(
            countryCode = CountryCodeManager.getOrAutoDetectCountry(appContext)?.dial_code
                ?: CountryCodeManager.getSelectedDialCode(appContext)
        )
        _messages.value = listOf(
            ChatMessage(
                text = localized(
                    "Setup reset. Let us start again.",
                    "Setup reset ho gaya. Chalo dobara start karte hain."
                ),
                type = MessageType.TEXT_BOT
            )
        )

        if (_groups.value.isEmpty()) {
            appendCardOnce(MessageType.CARD_ADD_CONTACT)
            _step.value = AgentStep.WAITING_FOR_CONTACTS
            addBotMessage(
                english = "Create a group first by tapping Add Contact Now.",
                hindi = "Add Contact Now tap karke pehle group banao."
            )
        } else {
            appendCardOnce(MessageType.CHIPS_CONTACT_OPTIONS)
            _step.value = AgentStep.SELECT_CONTACT_SOURCE
            addBotMessage(
                english = "Would you like to use an existing group or create a new one?",
                hindi = "Purana group use karna hai ya naya banana hai?"
            )
        }
    }

    private fun updateLanguagePreference(text: String) {
        val normalized = text.lowercase()
        val explicitEnglish = containsAny(
            normalized,
            "speak in english",
            "english please",
            "reply in english",
            "language english",
            "use english"
        )
        val explicitHindi = containsAny(
            normalized,
            "speak in hindi",
            "reply in hindi",
            "language hindi",
            "hindi me",
            "hindi mein",
            "use hindi"
        ) || containsHindiScript(text)

        _language.value = when {
            explicitEnglish -> ChatLanguage.ENGLISH
            explicitHindi -> ChatLanguage.HINDI
            detectHindiIntent(text) -> ChatLanguage.HINDI
            else -> _language.value
        }
    }

    private fun detectHindiIntent(text: String): Boolean {
        val lowered = text.lowercase()
        val tokens = lowered.split(Regex("[^a-zA-Z]+")).filter { it.isNotBlank() }
        val hindiWordSignals = setOf(
            "kya",
            "kaise",
            "karna",
            "nahi",
            "batao",
            "samjha",
            "purana",
            "naya",
            "bhejna",
            "likho",
            "naam",
            "badal",
            "chahiye",
            "mujhe",
            "wala"
        )
        val hindiPhraseSignals = listOf(
            "kar do",
            "karna hai",
            "ho gaya",
            "hogaya",
            "badal do"
        )
        val wordScore = tokens.count { it in hindiWordSignals }
        val phraseScore = hindiPhraseSignals.count { lowered.contains(it) }
        return wordScore + phraseScore >= 2
    }

    private fun containsHindiScript(text: String): Boolean {
        return text.any { it.code in 0x0900..0x097F }
    }

    private fun detectCampaignTypeFromText(normalizedText: String): CampaignType? {
        return when {
            containsAny(normalizedText, "sheet", "excel", "csv") -> CampaignType.SHEET
            containsAny(normalizedText, "text and media", "text+media", "text media", "separate media") -> CampaignType.TEXT_MEDIA
            containsAny(normalizedText, "caption", "media with caption") -> CampaignType.CAPTION
            containsAny(normalizedText, "text campaign", "text only", "only text") -> CampaignType.TEXT
            containsAny(normalizedText, "image", "video", "pdf") -> CampaignType.CAPTION
            containsAny(normalizedText, "text") -> CampaignType.TEXT
            else -> null
        }
    }

    private fun detectExplicitCampaignType(normalizedText: String): CampaignType? {
        return if (
            containsAny(
                normalizedText,
                "campaign type",
                "type is",
                "use text",
                "use caption",
                "use sheet",
                "use text and media",
                "choose text",
                "choose caption",
                "choose sheet",
                "choose text and media"
            )
        ) {
            detectCampaignTypeFromText(normalizedText)
        } else {
            null
        }
    }

    private fun extractCampaignName(rawText: String): String? {
        return extractAfterKeyword(rawText, "campaign name")
            ?: extractAfterKeyword(rawText, "call it")
            ?: extractAfterKeyword(rawText, "name this campaign")
            ?: extractAfterKeyword(rawText, "set campaign name")
    }

    private fun extractExplicitMessage(rawText: String): String? {
        return extractAfterKeyword(rawText, "message:")
            ?: extractAfterKeyword(rawText, "message is")
            ?: extractAfterKeyword(rawText, "message")
                ?.takeIf { rawText.lowercase().startsWith("message") }
            ?: extractAfterKeyword(rawText, "send this")
            ?: extractAfterKeyword(rawText, "text:")
            ?: extractAfterKeyword(rawText, "caption:")
    }

    private fun extractAfterKeyword(rawText: String, keyword: String): String? {
        val lowered = rawText.lowercase()
        val index = lowered.indexOf(keyword.lowercase())
        if (index == -1) return null

        var value = rawText.substring(index + keyword.length).trim()
        value = value.trimStart(':', '-', '=').trim()

        listOf("is ", "to ", "as ").forEach { prefix ->
            if (value.lowercase().startsWith(prefix)) {
                value = value.substring(prefix.length).trim()
            }
        }

        return value
            .trim()
            .trim('"', '\'')
            .takeIf { it.isNotBlank() }
    }

    private fun findBestGroupFromText(rawText: String): Group? {
        val normalizedInput = normalizeForMatch(rawText)
        if (normalizedInput.isBlank()) return null

        return _groups.value
            .map { group -> group to groupMatchScore(normalizedInput, normalizeForMatch(group.name)) }
            .filter { (_, score) -> score >= 20 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun groupMatchScore(input: String, groupName: String): Int {
        if (input == groupName) return 100
        if (input.contains(groupName)) return 90
        if (groupName.contains(input) && input.length >= 4) return 60

        val inputTokens = input.split(" ").filter { it.isNotBlank() }.toSet()
        val groupTokens = groupName.split(" ").filter { it.isNotBlank() }.toSet()
        if (inputTokens.isEmpty() || groupTokens.isEmpty()) return 0

        val overlap = inputTokens.intersect(groupTokens).size
        return when {
            overlap == groupTokens.size -> 80
            overlap >= 2 -> 40 + overlap * 10
            overlap == 1 -> 20
            else -> 0
        }
    }

    private fun normalizeForMatch(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsAny(text: String, vararg terms: String): Boolean {
        return terms.any { text.contains(it) }
    }

    private fun emitUiAction(action: AgentUiAction) {
        _uiActions.tryEmit(action)
    }

    private fun appendCardOnce(type: MessageType) {
        if (_messages.value.any { it.type == type }) {
            return
        }
        _messages.value = _messages.value + ChatMessage(type = type)
    }

    private fun addBotMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text = text, type = MessageType.TEXT_BOT)
    }

    private fun addBotMessage(english: String, hindi: String) {
        addBotMessage(localized(english, hindi))
    }

    private fun addUserMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text = text, type = MessageType.TEXT_USER)
    }

    private fun localized(english: String, hindi: String): String {
        return if (_language.value == ChatLanguage.HINDI) hindi else english
    }

    private fun resolveFileName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }
}
