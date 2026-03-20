package com.message.bulksend.autorespond.menureply

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

// Beautiful Modern Color Palette - Clean & Attractive
object SubmenuColors {
    val DarkBackground = Color(0xFF0F0F23) // Rich dark blue
    val CardBackground = Color(0xFF1E293B) // Elegant slate
    val CardBackgroundSecondary = Color(0xFF1E40AF) // Beautiful blue
    val CardBackgroundTertiary = Color(0xFF059669) // Fresh green
    val CardBackgroundQuaternary = Color(0xFF7C2D92) // Elegant purple
    val CardBackgroundCyan = Color(0xFF0891B2) // Ocean cyan
    
    // Beautiful bright accent colors
    val AccentPrimary = Color(0xFF8B5CF6) // Vibrant purple
    val AccentSecondary = Color(0xFF06B6D4) // Cyan blue
    val AccentPink = Color(0xFFEC4899) // Hot pink
    val AccentGreen = Color(0xFF10B981) // Emerald green
    val AccentEmerald = Color(0xFF14B8A6) // Teal
    val AccentRose = Color(0xFFF472B6) // Rose pink
    val AccentMint = Color(0xFF34D399) // Mint green
    
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFD1D5DB)
    val TextTertiary = Color(0xFF9CA3AF)
}

class SubmenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val parentId = intent.getStringExtra("parent_id") ?: ""
        val parentTitle = intent.getStringExtra("parent_title") ?: "Submenu"
        setContent { BulksendTestTheme { SubmenuScreen(parentId, parentTitle) { finish() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmenuScreen(parentId: String, parentTitle: String, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val menuReplyManager = remember { MenuReplyManager(context) }
    
    var subMessage by remember { mutableStateOf("select sub option") }
    var finalMessage by remember { mutableStateOf("") } // Final message field
    var subItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }
    var showRootEditDialog by remember { mutableStateOf(false) }
    var showFinalMessageDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(parentId) {
        if (parentId.isNotEmpty()) {
            isLoading = true
            // Load parent item to get submenuMessage and responseMessage
            val parentItem = menuReplyManager.getMenuItemById(parentId)
            if (parentItem != null) {
                subMessage = parentItem.submenuMessage.ifEmpty { "select sub option" }
                finalMessage = parentItem.responseMessage // Load final message
            }
            subItems = menuReplyManager.getChildrenByParentId(parentId)
            isLoading = false
        }
    }
    
    fun refreshData() {
        scope.launch { 
            subItems = menuReplyManager.getChildrenByParentId(parentId)
            // Reload parent item data
            val parentItem = menuReplyManager.getMenuItemById(parentId)
            if (parentItem != null) {
                finalMessage = parentItem.responseMessage
            }
        }
    }
    
    // Save submenuMessage to parent item
    fun saveSubmenuMessage(message: String) {
        scope.launch {
            val parentItem = menuReplyManager.getMenuItemById(parentId)
            if (parentItem != null) {
                menuReplyManager.updateMenuItem(parentItem.copy(submenuMessage = message))
            }
        }
    }
    
    // Save final message to parent item
    fun saveFinalMessage(message: String) {
        scope.launch {
            val parentItem = menuReplyManager.getMenuItemById(parentId)
            if (parentItem != null) {
                menuReplyManager.updateMenuItem(parentItem.copy(responseMessage = message))
                finalMessage = message
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Submenu", 
                        color = SubmenuColors.TextPrimary, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = { 
                    IconButton(onClick = onBackPressed) { 
                        Icon(Icons.Default.ArrowBack, "Back", tint = SubmenuColors.TextPrimary) 
                    } 
                },
                actions = {
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, InfoMenuActivity::class.java))
                    }) { 
                        Icon(Icons.Default.Info, "Help", tint = SubmenuColors.TextPrimary) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SubmenuColors.CardBackground)
            )
        },
        containerColor = SubmenuColors.DarkBackground
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SubmenuColors.AccentPrimary)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SubmenuColors.DarkBackground,
                                Color(0xFF0F0F12)
                            )
                        )
                    )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                item { SubParentContext(parentTitle) }
                
                // Connector line from parent to root
                item {
                    Box(modifier = Modifier.padding(start = 16.dp).width(2.dp).height(12.dp).background(SubmenuColors.TextTertiary))
                }
                
                item { SubRootCard(subMessage) { showRootEditDialog = true } }
                
                item { SubmenuHintCard() }
                
                itemsIndexed(subItems) { index, item ->
                    SubTreeItem(
                        item = item,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == subItems.size - 1,
                        onSubmenuClick = {
                            context.startActivity(Intent(context, SubmenuActivity::class.java).apply {
                                putExtra("parent_id", item.id)
                                putExtra("parent_title", item.title)
                            })
                        },
                        onMoreClick = { editingItem = item }
                    )
                }
                
                item { SubAddButton { showAddDialog = true } }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    }
    
    // Dialogs
    if (showRootEditDialog) {
        SubRootEditDialog(subMessage, { showRootEditDialog = false }) { msg ->
            subMessage = msg
            saveSubmenuMessage(msg)
            showRootEditDialog = false
        }
    }
    
    if (showFinalMessageDialog) {
        FinalMessageEditDialog(
            currentMessage = finalMessage,
            onDismiss = { showFinalMessageDialog = false }
        ) { message ->
            saveFinalMessage(message)
            showFinalMessageDialog = false
        }
    }
    
    if (showAddDialog) {
        SubAddDialog({ showAddDialog = false }) { title, hasSub, finalMsg ->
            scope.launch {
                menuReplyManager.addMenuItem(MenuItem(
                    title = title, 
                    orderIndex = subItems.size, 
                    hasSubmenu = hasSub, 
                    parentId = parentId,
                    responseMessage = if (hasSub) "" else finalMsg
                ))
                refreshData()
                showAddDialog = false
            }
        }
    }
    
    if (editingItem != null) {
        SubEditDialog(
            item = editingItem!!,
            onDismiss = { editingItem = null },
            onSave = { title, hasSub, responseMsg ->
                scope.launch {
                    menuReplyManager.updateMenuItem(
                        editingItem!!.copy(
                            title = title,
                            hasSubmenu = hasSub,
                            responseMessage = if (hasSub) "" else responseMsg
                        )
                    )
                    refreshData()
                    editingItem = null
                }
            },
            onDelete = {
                scope.launch {
                    menuReplyManager.deleteMenuItem(editingItem!!.id)
                    refreshData()
                    editingItem = null
                }
            },
            onOpenSubmenu = {
                context.startActivity(Intent(context, SubmenuActivity::class.java).apply {
                    putExtra("parent_id", editingItem!!.id)
                    putExtra("parent_title", editingItem!!.title)
                })
                editingItem = null
            }
        )
    }
}

@Composable
fun SubParentContext(parentTitle: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SubmenuColors.CardBackgroundSecondary),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Text(
            parentTitle, 
            fontSize = 14.sp, 
            color = SubmenuColors.TextPrimary, 
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun SubmenuHintCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SubmenuColors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "How this submenu works",
                fontSize = 12.sp,
                color = SubmenuColors.AccentSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Add leaf items below. Final messages are shown only when a user selects an item.",
                fontSize = 14.sp,
                color = SubmenuColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun FinalMessageCard(finalMessage: String, hasItems: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (finalMessage.isNotEmpty()) 
                SubmenuColors.AccentEmerald.copy(alpha = 0.2f) 
            else 
                SubmenuColors.CardBackgroundSecondary
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Final Message", 
                    fontSize = 12.sp, 
                    color = SubmenuColors.AccentEmerald,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (finalMessage.isNotEmpty()) finalMessage else "Tap to add final message",
                    fontSize = 14.sp,
                    color = if (finalMessage.isNotEmpty()) SubmenuColors.TextPrimary else SubmenuColors.TextTertiary,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (hasItems && finalMessage.isNotEmpty()) {
                    Text(
                        "⚠️ Items will be disabled when final message is set",
                        fontSize = 11.sp,
                        color = Color(0xFFFF6B35),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (finalMessage.isNotEmpty()) Icons.Default.Edit else Icons.Default.Add,
                    "Edit Final Message", 
                    tint = SubmenuColors.AccentEmerald, 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun FinalMessageEditDialog(currentMessage: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var message by remember { mutableStateOf(currentMessage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final Message", color = SubmenuColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This message will be sent when user selects this option. Setting this will disable adding sub-items.",
                    fontSize = 12.sp,
                    color = SubmenuColors.TextSecondary
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Final Message", color = SubmenuColors.AccentSecondary) },
                    placeholder = { Text("Enter message to send...", color = SubmenuColors.TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SubmenuColors.AccentPrimary,
                        unfocusedBorderColor = SubmenuColors.TextTertiary,
                        focusedTextColor = SubmenuColors.TextPrimary,
                        unfocusedTextColor = SubmenuColors.TextSecondary
                    )
                )
            }
        },
        confirmButton = { 
            Button(
                onClick = { onSave(message.trim()) }, 
                colors = ButtonDefaults.buttonColors(containerColor = SubmenuColors.AccentPrimary)
            ) { 
                Text("Save") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = SubmenuColors.TextSecondary) 
            } 
        },
        containerColor = SubmenuColors.CardBackground
    )
}

@Composable
fun SubRootCard(message: String, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SubmenuColors.CardBackground),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message, 
                fontSize = 16.sp, 
                color = SubmenuColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreVert, 
                    "Edit", 
                    tint = SubmenuColors.TextPrimary, 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SubTreeItem(
    item: MenuItem,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onSubmenuClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val lineColor = SubmenuColors.TextTertiary
    val density = LocalDensity.current
    val lineWidthPx = with(density) { 2.dp.toPx() }
    val lineStartX = with(density) { 16.dp.toPx() }
    val horizontalLineEndX = with(density) { 32.dp.toPx() }
    // No extra height - line starts from item top edge only
    val extraTopPx = with(density) { 0.dp.toPx() }
    
    // Main item row with tree lines drawn behind
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val centerY = size.height / 2
                
                // Vertical line - for first item, start from negative (above) to reach root card
                val lineStartY = if (isFirst) -extraTopPx else 0f
                
                // Vertical line from top (or above for first) to center
                drawLine(
                    color = lineColor,
                    start = Offset(lineStartX, lineStartY),
                    end = Offset(lineStartX, centerY),
                    strokeWidth = lineWidthPx,
                    cap = StrokeCap.Square
                )
                
                // Vertical line from center to bottom (if not last)
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(lineStartX, centerY),
                        end = Offset(lineStartX, size.height),
                        strokeWidth = lineWidthPx,
                        cap = StrokeCap.Square
                    )
                }
                
                // Horizontal line from vertical to card
                drawLine(
                    color = lineColor,
                    start = Offset(lineStartX, centerY),
                    end = Offset(horizontalLineEndX, centerY),
                    strokeWidth = lineWidthPx,
                    cap = StrokeCap.Square
                )
            }
            .padding(start = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (index % 5) {
                    0 -> SubmenuColors.CardBackgroundSecondary // Beautiful blue
                    1 -> SubmenuColors.CardBackgroundTertiary // Fresh green
                    2 -> SubmenuColors.CardBackgroundQuaternary // Elegant purple
                    3 -> SubmenuColors.CardBackgroundCyan // Ocean cyan
                    else -> SubmenuColors.CardBackground // Elegant slate
                }
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${item.title}", 
                        fontSize = 16.sp, 
                        color = SubmenuColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.hasSubmenu) {
                        Text(
                            "Opens submenu",
                            fontSize = 12.sp,
                            color = when (index % 5) {
                                0 -> SubmenuColors.AccentSecondary // Cyan
                                1 -> SubmenuColors.AccentGreen // Emerald
                                2 -> SubmenuColors.AccentPink // Hot pink
                                3 -> SubmenuColors.AccentEmerald // Teal
                                else -> SubmenuColors.AccentMint // Mint
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSubmenuClick() }
                                .padding(top = 4.dp)
                        )
                    } else if (item.responseMessage.isNotEmpty()) {
                        Text(
                            "Sends: ${item.responseMessage.take(40)}${if (item.responseMessage.length > 40) "..." else ""}",
                            fontSize = 11.sp,
                            color = SubmenuColors.TextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert, 
                        "More", 
                        tint = SubmenuColors.TextPrimary, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SubAddButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 32.dp)) {
        OutlinedCard(
            modifier = Modifier.clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = Color.Transparent
            ),
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                SubmenuColors.AccentSecondary
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = SubmenuColors.AccentSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ADD ITEM",
                    color = SubmenuColors.AccentSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Dialogs with Premium Theme
@Composable
fun SubRootEditDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var msg by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Submenu Message", color = SubmenuColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = msg, 
                onValueChange = { msg = it }, 
                label = { Text("Message", color = SubmenuColors.AccentSecondary) }, 
                modifier = Modifier.fillMaxWidth(), 
                singleLine = true, 
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SubmenuColors.AccentPrimary,
                    unfocusedBorderColor = SubmenuColors.TextTertiary,
                    focusedTextColor = SubmenuColors.TextPrimary,
                    unfocusedTextColor = SubmenuColors.TextSecondary
                )
            )
        },
        confirmButton = { 
            Button(
                onClick = { onSave(msg.trim()) }, 
                colors = ButtonDefaults.buttonColors(containerColor = SubmenuColors.AccentPrimary)
            ) { 
                Text("Save") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = SubmenuColors.TextSecondary) 
            } 
        },
        containerColor = SubmenuColors.CardBackground
    )
}

@Composable
fun SubAddDialog(onDismiss: () -> Unit, onSave: (String, Boolean, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var hasSub by remember { mutableStateOf(false) } // Default to false so user can add final message
    var finalMsg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Submenu Item", color = SubmenuColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title, 
                    onValueChange = { title = it }, 
                    label = { Text("Title", color = SubmenuColors.AccentSecondary) }, 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true, 
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SubmenuColors.AccentPrimary,
                        unfocusedBorderColor = SubmenuColors.TextTertiary,
                        focusedTextColor = SubmenuColors.TextPrimary,
                        unfocusedTextColor = SubmenuColors.TextSecondary
                    )
                )
                Row(
                    Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Has Submenu", color = SubmenuColors.TextPrimary)
                    Switch(
                        checked = hasSub, 
                        onCheckedChange = { hasSub = it }, 
                        colors = SwitchDefaults.colors(checkedTrackColor = SubmenuColors.AccentPrimary)
                    )
                }
                if (hasSub) {
                    Text(
                        text = "This item will open another submenu. Final reply will come from the leaf items inside it.",
                        fontSize = 12.sp,
                        color = SubmenuColors.TextSecondary
                    )
                } else {
                    OutlinedTextField(
                        value = finalMsg,
                        onValueChange = { finalMsg = it },
                        label = { Text("Final Message", color = SubmenuColors.AccentSecondary) },
                        placeholder = { Text("Message to send when this item is selected", color = SubmenuColors.TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SubmenuColors.AccentPrimary,
                            unfocusedBorderColor = SubmenuColors.TextTertiary,
                            focusedTextColor = SubmenuColors.TextPrimary,
                            unfocusedTextColor = SubmenuColors.TextSecondary
                        )
                    )
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), hasSub, if (hasSub) "" else finalMsg.trim()) }, 
                colors = ButtonDefaults.buttonColors(containerColor = SubmenuColors.AccentPrimary)
            ) { 
                Text("Add") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = SubmenuColors.TextSecondary) 
            } 
        },
        containerColor = SubmenuColors.CardBackground
    )
}

@Composable
fun SubEditDialog(item: MenuItem, onDismiss: () -> Unit, onSave: (String, Boolean, String) -> Unit, onDelete: () -> Unit, onOpenSubmenu: () -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var hasSub by remember { mutableStateOf(item.hasSubmenu) }
    var responseMsg by remember { mutableStateOf(item.responseMessage) }
    var showDel by remember { mutableStateOf(false) }
    
    if (showDel) {
        AlertDialog(
            onDismissRequest = { showDel = false },
            title = { Text("Delete?", color = SubmenuColors.TextPrimary) },
            text = { Text("Delete \"${item.title}\"?", color = SubmenuColors.TextSecondary) },
            confirmButton = { 
                Button(
                    onClick = onDelete, 
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { 
                    Text("Delete") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showDel = false }) { 
                    Text("Cancel", color = SubmenuColors.TextSecondary) 
                } 
            },
            containerColor = SubmenuColors.CardBackground
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Item", color = SubmenuColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title, 
                        onValueChange = { title = it }, 
                        label = { Text("Title", color = SubmenuColors.AccentSecondary) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true, 
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SubmenuColors.AccentPrimary,
                            unfocusedBorderColor = SubmenuColors.TextTertiary,
                            focusedTextColor = SubmenuColors.TextPrimary,
                            unfocusedTextColor = SubmenuColors.TextSecondary
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Has Submenu", color = SubmenuColors.TextPrimary)
                        Switch(
                            checked = hasSub, 
                            onCheckedChange = { hasSub = it }, 
                            colors = SwitchDefaults.colors(checkedTrackColor = SubmenuColors.AccentPrimary)
                        )
                    }
                    if (hasSub) {
                        Text(
                            text = "This item opens another submenu. Final reply should be added to the leaf items inside it.",
                            fontSize = 12.sp,
                            color = SubmenuColors.TextSecondary
                        )
                    } else {
                        OutlinedTextField(
                            value = responseMsg,
                            onValueChange = { responseMsg = it },
                            label = { Text("Final Message", color = SubmenuColors.AccentSecondary) },
                            placeholder = { Text("Message to send when this item is selected", color = SubmenuColors.TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SubmenuColors.AccentPrimary,
                                unfocusedBorderColor = SubmenuColors.TextTertiary,
                                focusedTextColor = SubmenuColors.TextPrimary,
                                unfocusedTextColor = SubmenuColors.TextSecondary
                            )
                        )
                    }
                    if (hasSub) {
                        Button(
                            onClick = onOpenSubmenu, 
                            modifier = Modifier.fillMaxWidth(), 
                            colors = ButtonDefaults.buttonColors(containerColor = SubmenuColors.AccentSecondary)
                        ) {
                            Icon(Icons.Default.SubdirectoryArrowRight, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Edit Submenu")
                        }
                    }
                    TextButton(
                        onClick = { showDel = true }, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete", color = Color.Red)
                    }
                }
            },
            confirmButton = { 
                Button(
                    onClick = { if (title.isNotBlank()) onSave(title.trim(), hasSub, if (hasSub) "" else responseMsg.trim()) }, 
                    colors = ButtonDefaults.buttonColors(containerColor = SubmenuColors.AccentPrimary)
                ) { 
                    Text("Save") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = onDismiss) { 
                    Text("Cancel", color = SubmenuColors.TextSecondary) 
                } 
            },
            containerColor = SubmenuColors.CardBackground
        )
    }
}
