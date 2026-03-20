package com.message.bulksend.autorespond.menureply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

/**
 * Create Menu Activity - Menu Builder Screen
 */
class CreateMenuActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                CreateMenuScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMenuScreen(
    onBackPressed: () -> Unit,
    viewModel: MenuCreationViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MenuItem?>(null) }
    
    // Load menu items on first composition
    LaunchedEffect(Unit) {
        viewModel.loadMenuItems()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Create menu", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back", 
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF00A693)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        viewModel.saveMenu()
                    }
                },
                containerColor = Color(0xFF00A693),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        },
        containerColor = Color(0xFFE5DDD5)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Section
            item {
                MessageBubblePreview(
                    message = viewModel.generatePreviewText(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Root Message Input
            item {
                RootMessageInput(
                    message = state.rootMessage,
                    onMessageChange = viewModel::updateRootMessage
                )
            }
            
            // Menu Items List
            items(state.menuItems) { item ->
                MenuItemCard(
                    item = item,
                    onEdit = { editingItem = it },
                    onDelete = { 
                        scope.launch {
                            viewModel.deleteMenuItem(it.id)
                        }
                    },
                    onToggleSubmenu = {
                        scope.launch {
                            viewModel.toggleSubmenu(it.id)
                        }
                    }
                )
            }
            
            // Add List Button
            item {
                AddListButton(
                    onClick = { showAddDialog = true }
                )
            }
            
            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog || editingItem != null) {
        AddMenuItemDialog(
            item = editingItem,
            onDismiss = { 
                showAddDialog = false
                editingItem = null
            },
            onSave = { title, description, hasSubmenu ->
                scope.launch {
                    if (editingItem != null) {
                        viewModel.updateMenuItem(
                            editingItem!!.copy(
                                title = title,
                                description = description,
                                hasSubmenu = hasSubmenu
                            )
                        )
                    } else {
                        viewModel.addMenuItem(title, description, hasSubmenu)
                    }
                    showAddDialog = false
                    editingItem = null
                }
            }
        )
    }
}

@Composable
fun MessageBubblePreview(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFDCF8C6) // WhatsApp message bubble green
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            fontSize = 14.sp,
            color = Color.Black,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun RootMessageInput(
    message: String,
    onMessageChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Type a message",
            fontSize = 14.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("select option") },
            trailingIcon = {
                if (message.isNotEmpty()) {
                    IconButton(onClick = { onMessageChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00A693),
                unfocusedBorderColor = Color(0xFF999999)
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun MenuItemCard(
    item: MenuItem,
    onEdit: (MenuItem) -> Unit,
    onDelete: (MenuItem) -> Unit,
    onToggleSubmenu: (MenuItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "List ${item.orderIndex + 1}",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    if (item.hasSubmenu) {
                        Text(
                            text = "SUBMENU",
                            fontSize = 12.sp,
                            color = Color(0xFF00A693),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // More options menu
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                onEdit(item)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.hasSubmenu) "Remove Submenu" else "Add Submenu") },
                            onClick = {
                                onToggleSubmenu(item)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (item.hasSubmenu) Icons.Default.Remove else Icons.Default.Add,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                onDelete(item)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = null,
                                    tint = Color.Red
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddListButton(
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = Color(0xFF00A693)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "+ ADD LIST",
            color = Color(0xFF00A693),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AddMenuItemDialog(
    item: MenuItem? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var hasSubmenu by remember { mutableStateOf(item?.hasSubmenu ?: false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item != null) "Edit Menu Item" else "Add Menu Item")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Has Submenu")
                    Switch(
                        checked = hasSubmenu,
                        onCheckedChange = { hasSubmenu = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00A693)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), description.trim(), hasSubmenu)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A693)
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}