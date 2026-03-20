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
object MenuColors {
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

class MenuReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BulksendTestTheme { MenuReplyScreen(onBackPressed = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuReplyScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val menuReplyManager = remember { MenuReplyManager(context) }
    
    var rootMessage by remember { mutableStateOf("select option") }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }
    var showRootEditDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isLoading = true
        rootMessage = menuReplyManager.getRootMessage()
        menuItems = menuReplyManager.getRootMenuItems()
        isLoading = false
    }
    
    fun refreshData() {
        scope.launch {
            rootMessage = menuReplyManager.getRootMessage()
            menuItems = menuReplyManager.getRootMenuItems()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Menu Reply", 
                        color = MenuColors.TextPrimary, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = { 
                    IconButton(onClick = onBackPressed) { 
                        Icon(Icons.Default.ArrowBack, "Back", tint = MenuColors.TextPrimary) 
                    } 
                },
                actions = {
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, InfoMenuActivity::class.java))
                    }) { 
                        Icon(Icons.Default.Info, "Help", tint = MenuColors.TextPrimary) 
                    }
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, MenuReplySettingsActivity::class.java))
                    }) { 
                        Icon(Icons.Default.Settings, "Settings", tint = MenuColors.TextPrimary) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MenuColors.CardBackground)
            )
        },
        containerColor = MenuColors.DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MenuColors.DarkBackground,
                            Color(0xFF0F0F12)
                        )
                    )
                )
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MenuColors.AccentPrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    item { RootCard(rootMessage) { showRootEditDialog = true } }
                    
                    itemsIndexed(menuItems) { index, item ->
                        TreeMenuItem(
                            item = item,
                            index = index,
                            isFirst = index == 0,
                            isLast = index == menuItems.size - 1,
                            onSubmenuClick = {
                                context.startActivity(Intent(context, SubmenuActivity::class.java).apply {
                                    putExtra("parent_id", item.id)
                                    putExtra("parent_title", item.title)
                                })
                            },
                            onMoreClick = { editingItem = item }
                        )
                    }
                    
                    item { TreeAddButton { showAddDialog = true } }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
    
    // Dialogs
    if (showRootEditDialog) {
        RootEditDialog(rootMessage, { showRootEditDialog = false }) { msg ->
            scope.launch {
                menuReplyManager.saveRootMessage(msg)
                rootMessage = msg
                showRootEditDialog = false
            }
        }
    }
    
    if (showAddDialog) {
        AddDialog({ showAddDialog = false }) { title, hasSub, finalMsg ->
            scope.launch {
                menuReplyManager.addMenuItem(MenuItem(
                    title = title, 
                    orderIndex = menuItems.size, 
                    hasSubmenu = hasSub, 
                    parentId = null,
                    responseMessage = if (hasSub) "" else finalMsg
                ))
                refreshData()
                showAddDialog = false
            }
        }
    }
    
    if (editingItem != null) {
        EditDialog(
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
fun RootCard(message: String, onEdit: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MenuColors.CardBackground),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Menu Header",
                    fontSize = 12.sp,
                    color = MenuColors.AccentSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    message, 
                    fontSize = 16.sp, 
                    color = MenuColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit, 
                    "Edit", 
                    tint = MenuColors.AccentSecondary, 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TreeMenuItem(
    item: MenuItem,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onSubmenuClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val lineColor = MenuColors.TextTertiary
    val density = LocalDensity.current
    val lineWidthPx = with(density) { 2.dp.toPx() }
    val lineStartX = with(density) { 16.dp.toPx() }
    val horizontalLineEndX = with(density) { 32.dp.toPx() }
    val extraTopPx = with(density) { 0.dp.toPx() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val centerY = size.height / 2
                
                val lineStartY = if (isFirst) -extraTopPx else 0f
                
                drawLine(
                    color = lineColor,
                    start = Offset(lineStartX, lineStartY),
                    end = Offset(lineStartX, centerY),
                    strokeWidth = lineWidthPx,
                    cap = StrokeCap.Square
                )
                
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(lineStartX, centerY),
                        end = Offset(lineStartX, size.height),
                        strokeWidth = lineWidthPx,
                        cap = StrokeCap.Square
                    )
                }
                
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
                    0 -> MenuColors.CardBackgroundSecondary // Beautiful blue
                    1 -> MenuColors.CardBackgroundTertiary // Fresh green
                    2 -> MenuColors.CardBackgroundQuaternary // Elegant purple
                    3 -> MenuColors.CardBackgroundCyan // Ocean cyan
                    else -> MenuColors.CardBackground // Elegant slate
                }
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${item.title}", 
                        fontSize = 16.sp, 
                        color = MenuColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.hasSubmenu) {
                        Text(
                            "Opens submenu",
                            fontSize = 12.sp,
                            color = when (index % 5) {
                                0 -> MenuColors.AccentSecondary // Cyan
                                1 -> MenuColors.AccentGreen // Emerald
                                2 -> MenuColors.AccentPink // Hot pink
                                3 -> MenuColors.AccentEmerald // Teal
                                else -> MenuColors.AccentMint // Mint
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
                            color = MenuColors.TextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert, 
                        "More", 
                        tint = MenuColors.TextPrimary, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TreeAddButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 32.dp)
    ) {
        OutlinedCard(
            modifier = Modifier.clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(2.dp, MenuColors.AccentSecondary)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MenuColors.AccentSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "ADD MENU ITEM",
                    color = MenuColors.AccentSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Dialogs with Premium Theme
@Composable
fun RootEditDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var msg by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Menu Header", color = MenuColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = msg, 
                onValueChange = { msg = it },
                label = { Text("Header", color = MenuColors.AccentSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MenuColors.AccentPrimary,
                    unfocusedBorderColor = MenuColors.TextTertiary,
                    focusedTextColor = MenuColors.TextPrimary,
                    unfocusedTextColor = MenuColors.TextSecondary
                )
            )
        },
        confirmButton = { 
            Button(
                onClick = { onSave(msg.trim()) }, 
                colors = ButtonDefaults.buttonColors(containerColor = MenuColors.AccentPrimary)
            ) { 
                Text("Save") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = MenuColors.TextSecondary) 
            } 
        },
        containerColor = MenuColors.CardBackground
    )
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onSave: (String, Boolean, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var hasSub by remember { mutableStateOf(false) }
    var finalMsg by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Menu Item", color = MenuColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title, 
                    onValueChange = { title = it },
                    label = { Text("Title", color = MenuColors.AccentSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MenuColors.AccentPrimary,
                        unfocusedBorderColor = MenuColors.TextTertiary,
                        focusedTextColor = MenuColors.TextPrimary,
                        unfocusedTextColor = MenuColors.TextSecondary
                    )
                )
                Row(
                    Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Has Submenu", color = MenuColors.TextPrimary)
                    Switch(
                        checked = hasSub, 
                        onCheckedChange = { hasSub = it }, 
                        colors = SwitchDefaults.colors(checkedTrackColor = MenuColors.AccentPrimary)
                    )
                }
                if (hasSub) {
                    Text(
                        text = "This item will open a submenu. Final reply will be handled by the items inside it.",
                        fontSize = 12.sp,
                        color = MenuColors.TextSecondary
                    )
                } else {
                    OutlinedTextField(
                        value = finalMsg,
                        onValueChange = { finalMsg = it },
                        label = { Text("Final Message", color = MenuColors.AccentSecondary) },
                        placeholder = { Text("Message to send when this item is selected", color = MenuColors.TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MenuColors.AccentPrimary,
                            unfocusedBorderColor = MenuColors.TextTertiary,
                            focusedTextColor = MenuColors.TextPrimary,
                            unfocusedTextColor = MenuColors.TextSecondary
                        )
                    )
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), hasSub, finalMsg.trim()) }, 
                colors = ButtonDefaults.buttonColors(containerColor = MenuColors.AccentPrimary)
            ) { 
                Text("Add") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = MenuColors.TextSecondary) 
            } 
        },
        containerColor = MenuColors.CardBackground
    )
}

@Composable
fun EditDialog(
    item: MenuItem, 
    onDismiss: () -> Unit, 
    onSave: (String, Boolean, String) -> Unit, 
    onDelete: () -> Unit, 
    onOpenSubmenu: () -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var hasSub by remember { mutableStateOf(item.hasSubmenu) }
    var responseMsg by remember { mutableStateOf(item.responseMessage) }
    var showDel by remember { mutableStateOf(false) }
    
    if (showDel) {
        AlertDialog(
            onDismissRequest = { showDel = false },
            title = { Text("Delete Item?", color = MenuColors.TextPrimary) },
            text = { Text("Delete \"${item.title}\"?", color = MenuColors.TextSecondary) },
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
                    Text("Cancel", color = MenuColors.TextSecondary) 
                } 
            },
            containerColor = MenuColors.CardBackground
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Item", color = MenuColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title, 
                        onValueChange = { title = it }, 
                        label = { Text("Title", color = MenuColors.AccentSecondary) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true, 
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MenuColors.AccentPrimary,
                            unfocusedBorderColor = MenuColors.TextTertiary,
                            focusedTextColor = MenuColors.TextPrimary,
                            unfocusedTextColor = MenuColors.TextSecondary
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Has Submenu", color = MenuColors.TextPrimary)
                        Switch(
                            checked = hasSub, 
                            onCheckedChange = { hasSub = it }, 
                            colors = SwitchDefaults.colors(checkedTrackColor = MenuColors.AccentPrimary)
                        )
                    }
                    if (hasSub) {
                        Text(
                            text = "This item opens a submenu. Final reply should be added to the leaf items inside it.",
                            fontSize = 12.sp,
                            color = MenuColors.TextSecondary
                        )
                    } else {
                        OutlinedTextField(
                            value = responseMsg,
                            onValueChange = { responseMsg = it },
                            label = { Text("Final Message", color = MenuColors.AccentSecondary) },
                            placeholder = { Text("Message to send when this item is selected", color = MenuColors.TextTertiary) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MenuColors.AccentPrimary,
                                unfocusedBorderColor = MenuColors.TextTertiary,
                                focusedTextColor = MenuColors.TextPrimary,
                                unfocusedTextColor = MenuColors.TextSecondary
                            )
                        )
                    }
                    if (hasSub) {
                        Button(
                            onClick = onOpenSubmenu, 
                            modifier = Modifier.fillMaxWidth(), 
                            colors = ButtonDefaults.buttonColors(containerColor = MenuColors.AccentSecondary)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MenuColors.AccentPrimary)
                ) { 
                    Text("Save") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = onDismiss) { 
                    Text("Cancel", color = MenuColors.TextSecondary) 
                } 
            },
            containerColor = MenuColors.CardBackground
        )
    }
}
