package com.message.bulksend.leadmanager.importlead

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.model.LeadPriority

data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

@Composable
fun ManualImportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showContactsScreen by remember { mutableStateOf(false) }
    var phoneContacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var selectedContacts by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingContacts by remember { mutableStateOf(false) }
    
    var hasContactsPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    fun loadPhoneContacts() {
        coroutineScope.launch {
            isLoadingContacts = true
            try {
                val contacts = withContext(Dispatchers.IO) {
                    val contactsList = mutableListOf<PhoneContact>()
                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null,
                        null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                    )
                    
                    cursor?.use {
                        val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        
                        while (it.moveToNext()) {
                            val id = it.getString(idIndex)
                            val name = it.getString(nameIndex) ?: "Unknown"
                            val phone = it.getString(phoneIndex) ?: ""
                            
                            if (phone.isNotBlank()) {
                                contactsList.add(
                                    PhoneContact(
                                        id = id,
                                        name = name,
                                        phoneNumber = phone.replace(Regex("[^+\\d]"), "")
                                    )
                                )
                            }
                        }
                    }
                    contactsList.distinctBy { it.phoneNumber }
                }
                phoneContacts = contacts
                showContactsScreen = true
            } catch (e: Exception) {
                importResult = "Failed to load contacts: ${e.message}"
                showResultDialog = true
            } finally {
                isLoadingContacts = false
            }
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
        if (isGranted) {
            loadPhoneContacts()
        }
    }
    
    fun importSelectedContacts() {
        coroutineScope.launch {
            isImporting = true
            try {
                val contactsToImport = phoneContacts.filter { selectedContacts.contains(it.id) }
                
                // Convert contacts to leads
                val leadsToAdd = contactsToImport.map { contact ->
                    Lead(
                        id = java.util.UUID.randomUUID().toString(),
                        name = contact.name,
                        phoneNumber = contact.phoneNumber,
                        email = contact.email ?: "",
                        status = LeadStatus.NEW,
                        source = "Phone Contacts",
                        lastMessage = "Imported from phone contacts",
                        timestamp = System.currentTimeMillis(),
                        category = "Phone Import",
                        notes = "Imported from phone contacts on ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                        priority = LeadPriority.MEDIUM,
                        leadScore = 50
                    )
                }
                
                // Add leads to database using LeadManager
                val leadManager = com.message.bulksend.leadmanager.LeadManager(context)
                val inserted = withContext(Dispatchers.IO) {
                    val inserted = leadManager.addLeads(leadsToAdd)
                    return@withContext inserted
                }
                
                importResult = if (inserted < leadsToAdd.size) {
                    "Free plan can add only 5 leads.\nAdded $inserted / ${leadsToAdd.size} contacts. Upgrade to Chatspromo Premium to continue."
                } else {
                    "Successfully imported ${leadsToAdd.size} contacts to leads"
                }
                showResultDialog = true
                showContactsScreen = false
            } catch (e: Exception) {
                importResult = "Failed to import contacts: ${e.message}"
                showResultDialog = true
            } finally {
                isImporting = false
            }
        }
    }
    
    // Handle back press
    BackHandler { 
        if (showContactsScreen) {
            showContactsScreen = false
        } else {
            onBack()
        }
    }
    
    if (showContactsScreen) {
        // Contacts Selection Screen
        ContactsSelectionScreen(
            contacts = phoneContacts,
            selectedContacts = selectedContacts,
            onContactToggle = { contactId ->
                selectedContacts = if (selectedContacts.contains(contactId)) {
                    selectedContacts - contactId
                } else {
                    selectedContacts + contactId
                }
            },
            onSelectAll = {
                selectedContacts = phoneContacts.map { it.id }.toSet()
            },
            onDeselectAll = {
                selectedContacts = emptySet()
            },
            onImport = { importSelectedContacts() },
            onBack = { showContactsScreen = false },
            isImporting = isImporting
        )
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "Phone Contacts Import",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Import leads from your phone contacts",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        
        item {
            ManualImportCard(
                icon = Icons.Default.Contacts,
                title = "Phone Contacts",
                description = "Import contacts from your phone",
                status = if (hasContactsPermission) "Ready" else "Permission Required",
                statusColor = if (hasContactsPermission) Color(0xFF10B981) else Color(0xFFF59E0B),
                onClick = {
                    if (hasContactsPermission) {
                        loadPhoneContacts()
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            ManualImportInfoCard()
        }
    }
    
    // Loading Overlay for contacts
    if (isLoadingContacts) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading contacts...", color = Color.White)
                    Text("Please wait", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
    
    // Loading Overlay for import
    if (isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Importing contacts...", color = Color.White)
                    Text("Please wait", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
    
    // Result Dialog
    if (showResultDialog && importResult != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (importResult!!.startsWith("Successfully")) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (importResult!!.startsWith("Successfully")) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (importResult!!.startsWith("Successfully")) "Import Successful" else "Import Failed",
                        color = if (importResult!!.startsWith("Successfully")) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(importResult!!, color = Color(0xFF94A3B8))
            },
            confirmButton = {
                TextButton(onClick = { 
                    showResultDialog = false
                    if (importResult!!.startsWith("Successfully")) {
                        onBack()
                    }
                }) {
                    Text("OK", color = Color(0xFF10B981))
                }
            },
            containerColor = Color(0xFF1a1a2e)
        )
    }
}

@Composable
fun ManualImportCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = statusColor, 
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description, 
                    fontSize = 13.sp, 
                    color = Color(0xFF94A3B8)
                )
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Text(
                    status,
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ManualImportInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info, 
                    contentDescription = null, 
                    tint = Color(0xFF3B82F6), 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Phone Contacts Import", 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "• Phone contacts require permission\n" +
                "• Select individual or all contacts\n" +
                "• Contacts are converted to leads\n" +
                "• Duplicates are automatically detected",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSelectionScreen(
    contacts: List<PhoneContact>,
    selectedContacts: Set<String>,
    onContactToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    isImporting: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Select Contacts", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        Text(
                            "${selectedContacts.size} of ${contacts.size} selected",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF10B981))
                    }
                },
                actions = {
                    TextButton(
                        onClick = if (selectedContacts.size == contacts.size) onDeselectAll else onSelectAll
                    ) {
                        Text(
                            if (selectedContacts.size == contacts.size) "Deselect All" else "Select All",
                            color = Color(0xFF10B981)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        bottomBar = {
            if (selectedContacts.isNotEmpty()) {
                Surface(
                    color = Color(0xFF1a1a2e),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onImport,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            enabled = !isImporting
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importing...")
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import ${selectedContacts.size} Contacts")
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts) { contact ->
                    ContactItem(
                        contact = contact,
                        isSelected = selectedContacts.contains(contact.id),
                        onToggle = { onContactToggle(contact.id) }
                    )
                }
                
                if (contacts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ContactPhone,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No contacts found",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Make sure you have contacts saved on your phone",
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: PhoneContact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF1a1a2e)
        ),
        shape = RoundedCornerShape(12.dp)
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
                    .background(
                        if (isSelected) Color(0xFF10B981) else Color(0xFF64748B).copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    contact.phoneNumber,
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF10B981),
                    uncheckedColor = Color(0xFF64748B)
                )
            )
        }
    }
}
