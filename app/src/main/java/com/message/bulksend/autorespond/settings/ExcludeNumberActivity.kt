package com.message.bulksend.autorespond.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.message.bulksend.ui.theme.BulksendTestTheme

class ExcludeNumberActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                ExcludeNumberScreen(onBackPressed = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludeNumberScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val excludeManager = remember { ExcludeNumberManager(context) }
    
    var excludeEnabled by remember { mutableStateOf(excludeManager.isExcludeEnabled()) }
    var excludeSavedContacts by remember { mutableStateOf(excludeManager.isExcludeSavedContactsEnabled()) }
    var excludedContacts by remember { mutableStateOf(excludeManager.getExcludedContacts()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Check if contact permission is granted
    val hasContactPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactPermission.value = isGranted
        if (isGranted) {
            excludeSavedContacts = true
            excludeManager.setExcludeSavedContactsEnabled(true)
            Toast.makeText(context, "Contact permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Contact permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Permission Dialog
    if (showPermissionDialog) {
        ContactPermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onRequestPermission = {
                showPermissionDialog = false
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        )
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Add Number Dialog
    if (showAddDialog) {
        AddExcludeNumberDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { number, name ->
                if (excludeManager.addExcludedNumber(number, name)) {
                    excludedContacts = excludeManager.getExcludedContacts()
                }
                showAddDialog = false
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Exclude Numbers", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF00D4FF)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Exclude Saved Contacts Card (Main Feature)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (excludeSavedContacts) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF1a1a2e)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Contacts,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exclude My Contacts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Don't reply to saved contacts",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = excludeSavedContacts,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    // Check permission before enabling
                                    if (hasContactPermission.value) {
                                        excludeSavedContacts = true
                                        excludeManager.setExcludeSavedContactsEnabled(true)
                                    } else {
                                        // Show permission dialog
                                        showPermissionDialog = true
                                    }
                                } else {
                                    excludeSavedContacts = false
                                    excludeManager.setExcludeSavedContactsEnabled(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }
                }
            }
            
            // Info for saved contacts
            if (excludeSavedContacts) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Numbers saved in your phone contacts will NOT receive auto-replies",
                                fontSize = 13.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
            
            // Divider
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = Color(0xFF2D3748)
                )
            }
            
            // Manual Exclude Section Header
            item {
                Text(
                    "Manual Exclude List",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Enable/Disable Manual Exclude Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Manual Exclude", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                if (excludeEnabled) "Active - ${excludedContacts.size} numbers" 
                                else "Disabled",
                                fontSize = 13.sp,
                                color = if (excludeEnabled) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = excludeEnabled,
                            onCheckedChange = { 
                                excludeEnabled = it
                                excludeManager.setExcludeEnabled(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFEF4444)
                            )
                        )
                    }
                }
            }
            
            // Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Numbers in this list will NOT receive auto-replies",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
            
            // Section Header
            item {
                Text(
                    "Excluded Numbers (${excludedContacts.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            // Empty State
            if (excludedContacts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No excluded numbers", fontSize = 16.sp, color = Color(0xFF94A3B8))
                            Text("Tap + to add numbers", fontSize = 14.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }
            
            // Excluded Numbers List
            items(excludedContacts) { contact ->
                ExcludedContactCard(
                    contact = contact,
                    onDelete = {
                        excludeManager.removeExcludedNumber(contact.id)
                        excludedContacts = excludeManager.getExcludedContacts()
                    }
                )
            }
        }
    }
}


/**
 * Card for each excluded contact
 */
@Composable
fun ExcludedContactCard(
    contact: ExcludedContact,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.phoneNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (contact.name.isNotEmpty()) {
                    Text(
                        contact.name,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            
            // Delete Button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

/**
 * Dialog to add new excluded number
 */
@Composable
fun AddExcludeNumberDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PersonOff,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                "Add Excluded Number",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This number will not receive auto-replies",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
                
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number *") },
                    placeholder = { Text("+91 9876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFFEF4444),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (Optional)") },
                    placeholder = { Text("Contact name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFFEF4444),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (phoneNumber.isNotBlank()) {
                        onAdd(phoneNumber.trim(), name.trim())
                    }
                },
                enabled = phoneNumber.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}


/**
 * Dialog explaining why contact permission is needed
 */
@Composable
fun ContactPermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Contacts,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                "Contact Permission Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "To exclude your saved contacts from auto-reply, we need access to your contacts.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Your contacts are only checked locally on your device. We don't upload or store them anywhere.",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allow Access", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = Color(0xFF94A3B8))
            }
        }
    )
}
