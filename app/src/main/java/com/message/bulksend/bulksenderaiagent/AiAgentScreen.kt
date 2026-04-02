package com.message.bulksend.bulksenderaiagent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.contactmanager.Group

@Composable
fun AiAgentScreen(
    viewModel: ChatViewModel,
    onAddContactClick: () -> Unit,
    onPickAttachment: (AttachmentKind) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onLaunchCampaign: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val language by viewModel.language.collectAsState()
    val step by viewModel.step.collectAsState()
    val attachmentRequest by viewModel.attachmentRequest.collectAsState()
    val permissionChecklist by viewModel.permissionChecklist.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFE6DD))
    ) {
        TopAppBar(
            title = {
                Text(
                    localized(language, "AI Campaign Guide", "AI Campaign Guide"),
                    color = Color.White
                )
            },
            backgroundColor = Color(0xFF008069)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatItem(
                    message = message,
                    groups = groups,
                    draft = draft,
                    language = language,
                    step = step,
                    attachmentRequest = attachmentRequest,
                    permissionChecklist = permissionChecklist,
                    onAddContactClick = onAddContactClick,
                    onOptionSelected = viewModel::onOptionSelected,
                    onGroupSelected = viewModel::onGroupSelected,
                    onCampaignTypeSelected = viewModel::onCampaignTypeSelected,
                    onPickAttachment = onPickAttachment,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRefreshPermissions = onRefreshPermissions,
                    onLaunchCampaign = onLaunchCampaign
                )
            }
        }

        ChatInputBar(
            text = messageText,
            placeholder = placeholderForStep(step, language),
            onTextChange = { messageText = it },
            onSend = {
                if (messageText.isNotBlank()) {
                    viewModel.sendMessage(messageText)
                    messageText = ""
                }
            }
        )
    }
}

@Composable
private fun ChatItem(
    message: ChatMessage,
    groups: List<Group>,
    draft: CampaignDraft,
    language: ChatLanguage,
    step: AgentStep,
    attachmentRequest: AttachmentRequest?,
    permissionChecklist: PermissionChecklist,
    onAddContactClick: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onGroupSelected: (Group) -> Unit,
    onCampaignTypeSelected: (CampaignType) -> Unit,
    onPickAttachment: (AttachmentKind) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onLaunchCampaign: () -> Unit
) {
    when (message.type) {
        MessageType.TEXT_BOT -> BotMessageBubble(message.text)
        MessageType.TEXT_USER -> UserMessageBubble(message.text)
        MessageType.CARD_ADD_CONTACT -> {
            if (!draft.hasGroup() && step == AgentStep.WAITING_FOR_CONTACTS) {
                AddContactCard(language, onAddContactClick)
            }
        }
        MessageType.CHIPS_CONTACT_OPTIONS -> {
            if (step == AgentStep.SELECT_CONTACT_SOURCE) {
                ContactOptionsChips(language, onOptionSelected)
            }
        }
        MessageType.CARD_GROUP_LIST -> {
            if (groups.isNotEmpty() && (step == AgentStep.SELECT_GROUP || draft.hasGroup())) {
                GroupListCard(
                    groups = groups,
                    language = language,
                    selectedGroupId = draft.selectedGroupId,
                    onGroupSelected = onGroupSelected
                )
            }
        }
        MessageType.CARD_CAMPAIGN_TYPES -> {
            if (draft.message.isNotBlank() && (step == AgentStep.SELECT_CAMPAIGN_TYPE || draft.campaignType != null)) {
                CampaignTypeCardRow(
                    language = language,
                    selectedType = draft.campaignType,
                    onCampaignTypeSelected = onCampaignTypeSelected
                )
            }
        }
        MessageType.CARD_ATTACHMENT_REQUEST -> {
            attachmentRequest?.let {
                AttachmentRequestCard(
                    language = language,
                    request = it,
                    currentFileName = if (it.kind == AttachmentKind.MEDIA) draft.mediaName else draft.sheetName,
                    onPick = { onPickAttachment(it.kind) }
                )
            }
        }
        MessageType.CARD_PERMISSION_CHECKLIST -> {
            if (draft.isSetupComplete()) {
                PermissionChecklistCard(
                    language = language,
                    permissionChecklist = permissionChecklist,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRefreshPermissions = onRefreshPermissions
                )
            }
        }
        MessageType.CARD_LAUNCH_CAMPAIGN -> {
            if (draft.isSetupComplete() && permissionChecklist.requiredReady) {
                LaunchCampaignCard(
                    draft = draft,
                    language = language,
                    onLaunchCampaign = onLaunchCampaign
                )
            }
        }
    }
}

@Composable
private fun BotMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color.White,
            elevation = 2.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = Color.Black,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color(0xFFE2FFC7),
            elevation = 2.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = Color.Black,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun AddContactCard(language: ChatLanguage, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(18.dp),
            elevation = 5.dp,
            backgroundColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF008069), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = localized(language, "Add Contact Now", "Add Contact Now"),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF008069),
                        fontSize = 16.sp
                    )
                    Text(
                        text = localized(language, "Create a group and come back here", "Group banao aur chat me wapas aao"),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactOptionsChips(language: ChatLanguage, onOptionSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF008069),
            modifier = Modifier
                .padding(end = 8.dp)
                .clickable { onOptionSelected("old") }
        ) {
            Text(
                text = localized(language, "Use Existing Group", "Proceed with Old Contacts"),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                fontSize = 13.sp
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF128C7E),
            modifier = Modifier.clickable { onOptionSelected("new") }
        ) {
            Text(
                text = localized(language, "Create New Group", "Create New Group"),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun GroupListCard(
    groups: List<Group>,
    language: ChatLanguage,
    selectedGroupId: Long?,
    onGroupSelected: (Group) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            val isSelected = selectedGroupId == group.id
            Card(
                modifier = Modifier
                    .width(190.dp)
                    .height(116.dp)
                    .clickable { onGroupSelected(group) },
                backgroundColor = if (isSelected) Color(0xFFD9FDD3) else Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color(0xFF008069) else Color(0xFFE0E0E0)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = group.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${group.contacts.size} contacts",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusTag(
                            text = if (group.isPremiumGroup) {
                                localized(language, "Premium", "Premium")
                            } else {
                                localized(language, "Saved", "Saved")
                            },
                            background = if (group.isPremiumGroup) Color(0xFFFFF0C2) else Color(0xFFE8F5E9),
                            textColor = if (group.isPremiumGroup) Color(0xFF946200) else Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF008069),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignTypeCardRow(
    language: ChatLanguage,
    selectedType: CampaignType?,
    onCampaignTypeSelected: (CampaignType) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(CampaignType.values()) { type ->
            val accent = colorForCampaignType(type)
            val isSelected = selectedType == type

            Card(
                modifier = Modifier
                    .width(190.dp)
                    .height(148.dp)
                    .clickable { onCampaignTypeSelected(type) },
                backgroundColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                elevation = 4.dp,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accent else Color(0xFFE0E0E0)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(accent.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconForCampaignType(type),
                                contentDescription = null,
                                tint = accent
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = type.displayTitle(language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = type.displayDescription(language),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    if (isSelected) {
                        StatusTag(
                            text = localized(language, "Selected", "Selected"),
                            background = accent.copy(alpha = 0.16f),
                            textColor = accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRequestCard(
    language: ChatLanguage,
    request: AttachmentRequest,
    currentFileName: String?,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color.White,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFFF3E0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (request.kind == AttachmentKind.MEDIA) Icons.Filled.Image else Icons.Filled.TableChart,
                        contentDescription = null,
                        tint = Color(0xFFF57C00)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(request.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(request.description, color = Color.Gray, fontSize = 12.sp)
                }
            }

            if (!currentFileName.isNullOrBlank()) {
                StatusTag(
                    text = currentFileName,
                    background = Color(0xFFE8F5E9),
                    textColor = Color(0xFF1B5E20)
                )
            }

            Button(
                onClick = onPick,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF008069)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (currentFileName.isNullOrBlank()) request.buttonLabel else localized(language, "Change File", "Change File"),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PermissionChecklistCard(
    language: ChatLanguage,
    permissionChecklist: PermissionChecklist,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color.White,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = Color(0xFF008069)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(localized(language, "Permission Check", "Permission Check"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            PermissionRow(
                language = language,
                title = localized(language, "Accessibility Service", "Accessibility Service"),
                ready = permissionChecklist.accessibilityEnabled,
                actionLabel = localized(language, "Enable", "Enable"),
                onActionClick = onOpenAccessibilitySettings
            )
            PermissionRow(
                language = language,
                title = localized(language, "Overlay Permission", "Overlay Permission"),
                ready = permissionChecklist.overlayEnabled,
                actionLabel = localized(language, "Enable", "Enable"),
                onActionClick = onOpenOverlaySettings
            )
            PermissionRow(
                language = language,
                title = localized(language, "Notifications", "Notifications"),
                ready = permissionChecklist.notificationsEnabled,
                actionLabel = localized(language, "Allow", "Allow"),
                onActionClick = onRequestNotificationPermission
            )
            PermissionRow(
                language = language,
                title = localized(language, "WhatsApp Available", "WhatsApp Available"),
                ready = permissionChecklist.hasAnyWhatsApp,
                actionLabel = null,
                onActionClick = {}
            )

            OutlinedButton(
                onClick = onRefreshPermissions,
                border = BorderStroke(1.dp, Color(0xFF008069)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color(0xFF008069)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(localized(language, "Refresh Status", "Refresh Status"), color = Color(0xFF008069))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    language: ChatLanguage,
    title: String,
    ready: Boolean,
    actionLabel: String?,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = if (ready) localized(language, "Ready", "Ready") else localized(language, "Pending", "Pending"),
                color = if (ready) Color(0xFF1B5E20) else Color(0xFFD32F2F),
                fontSize = 12.sp
            )
        }

        if (!ready && actionLabel != null) {
            OutlinedButton(
                onClick = onActionClick,
                border = BorderStroke(1.dp, Color(0xFF008069)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel, color = Color(0xFF008069), fontSize = 12.sp)
            }
        } else if (ready) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
private fun LaunchCampaignCard(
    draft: CampaignDraft,
    language: ChatLanguage,
    onLaunchCampaign: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color.White,
        elevation = 5.dp,
        border = BorderStroke(1.dp, Color(0xFF008069))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFD9FDD3), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = Color(0xFF008069)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(localized(language, "Launch Filled Campaign", "Launch Filled Campaign"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        localized(
                            language,
                            "Tap below to open the target activity with auto-filled data",
                            "Neeche tap karte hi target activity auto-fill ke saath khulegi"
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            SummaryLine(localized(language, "Group", "Group"), draft.selectedGroupName.orEmpty())
            SummaryLine(localized(language, "Campaign Name", "Campaign Name"), draft.campaignName)
            SummaryLine(localized(language, "Message", "Message"), draft.message)
            SummaryLine(localized(language, "Type", "Type"), draft.campaignType?.displayTitle(language).orEmpty())

            if (!draft.mediaName.isNullOrBlank()) {
                SummaryLine(localized(language, "Media", "Media"), draft.mediaName)
            }
            if (!draft.sheetName.isNullOrBlank()) {
                SummaryLine(localized(language, "Sheet", "Sheet"), draft.sheetName)
            }

            Button(
                onClick = onLaunchCampaign,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF008069)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(localized(language, "Open Filled Screen", "Open Filled Screen"), color = Color.White)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun StatusTag(
    text: String,
    background: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            elevation = 2.dp
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(placeholder) },
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }

        FloatingActionButton(
            onClick = onSend,
            backgroundColor = Color(0xFF008069),
            contentColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send")
        }
    }
}

private fun placeholderForStep(step: AgentStep, language: ChatLanguage): String {
    return when (step) {
        AgentStep.ENTER_CAMPAIGN_NAME -> localized(language, "Type the campaign name...", "Campaign name likho...")
        AgentStep.ENTER_MESSAGE -> localized(language, "Type the campaign message...", "Campaign message type karo...")
        AgentStep.SELECT_GROUP -> localized(language, "Type the group name or tap a card...", "Group name likho ya card tap karo...")
        AgentStep.SELECT_CAMPAIGN_TYPE -> localized(language, "Type text, caption, media, or sheet...", "Text, caption, media ya sheet likho...")
        AgentStep.PICK_MEDIA -> localized(language, "Use the file picker card...", "File picker card use karo...")
        AgentStep.PICK_SHEET -> localized(language, "Use the sheet picker card...", "Sheet picker card use karo...")
        AgentStep.REVIEW_PERMISSIONS -> localized(language, "Type done or use refresh...", "Done likho ya refresh use karo...")
        AgentStep.READY_TO_LAUNCH -> localized(language, "Type if you want to change anything...", "Change chahiye to likho...")
        else -> localized(language, "Type a message...", "Type a message...")
    }
}

private fun localized(language: ChatLanguage, english: String, hindi: String): String {
    return if (language == ChatLanguage.HINDI) hindi else english
}

private fun colorForCampaignType(type: CampaignType): Color {
    return when (type) {
        CampaignType.TEXT -> Color(0xFF3949AB)
        CampaignType.CAPTION -> Color(0xFFD81B60)
        CampaignType.TEXT_MEDIA -> Color(0xFFF9A825)
        CampaignType.SHEET -> Color(0xFF2E7D32)
    }
}

private fun iconForCampaignType(type: CampaignType) = when (type) {
    CampaignType.TEXT -> Icons.Filled.Description
    CampaignType.CAPTION -> Icons.Filled.Image
    CampaignType.TEXT_MEDIA -> Icons.Filled.InsertDriveFile
    CampaignType.SHEET -> Icons.Filled.TableChart
}
