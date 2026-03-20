package com.message.bulksend.contactmanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.message.bulksend.auth.UserProfileActivity

import com.message.bulksend.utils.ContactLimitHandler
import com.message.bulksend.utils.PremiumUpgradeDialog
import com.message.bulksend.utils.SubscriptionUtils
import kotlinx.coroutines.launch


class ContactzActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactsTheme {
                ManageContactsScreen()
            }
        }
    }
}

@Composable
fun ContactsTheme(content: @Composable () -> Unit) {
    // Impressive dark theme with vibrant colors
    val colors = darkColorScheme(
        primary = Color(0xFF7C4DFF), // Vibrant Purple
        primaryContainer = Color(0xFF9E7CFF), // Lighter Purple
        secondary = Color(0xFF00E676), // Vibrant Green
        secondaryContainer = Color(0xFF69F0AE), // Lighter Green
        tertiary = Color(0xFF00B0FF), // Vibrant Blue
        tertiaryContainer = Color(0xFF40C4FF), // Lighter Blue
        surface = Color(0xFF1E1E2E), // Dark Navy
        surfaceVariant = Color(0xFF28293D), // Slightly Lighter Navy
        background = Color(0xFF121212), // Very Dark Blue-Black
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onTertiary = Color.White,
        onSurface = Color(0xFFE0E0FF), // Light Blue-White
        onBackground = Color(0xFFE0E0FF), // Light Blue-White
        outline = Color(0xFF5C6BC0), // Indigo
        outlineVariant = Color(0xFF7986CB), // Light Indigo
        error = Color(0xFFFF5252), // Vibrant Red
        onError = Color.White,
        scrim = Color(0xFF000000) // Black
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        ),
        content = content
    )
}

// Data class to hold information for each import option
data class ImportOptionInfo(val icon: ImageVector, val label: String, val color: Color, val onClick: () -> Unit)

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageContactsScreen() {
    val context = LocalContext.current
    val activity = (LocalContext.current as? Activity)
    val scope = rememberCoroutineScope()
    val contactsRepository = remember { ContactsRepository(context) }

    val savedGroups by contactsRepository.loadGroups().collectAsState(initial = emptyList())

    var importedContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var showPasteTextDialog by remember { mutableStateOf(false) }
    var showSheetsLinkDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var allWhatsAppContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var totalDeviceContacts by remember { mutableStateOf(0) }
    var isLoadingContacts by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0) }
    var loadingTotal by remember { mutableStateOf(0) }

    // Add contacts to existing group states
    var showAddContactsDialog by remember { mutableStateOf(false) }
    var selectedGroupForAddition by remember { mutableStateOf<Long?>(null) }

    // Premium Dialog States
    var showPremiumDialog by remember { mutableStateOf(false) }
    var currentContactCount by remember { mutableStateOf(0) }
    var contactLimit by remember { mutableStateOf(10) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Load contacts in background to prevent UI freeze
            isLoadingContacts = true
            Toast.makeText(context, "Loading contacts...", Toast.LENGTH_SHORT).show()

            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    totalDeviceContacts = contactsRepository.getTotalContactsCount()
                    allWhatsAppContacts = contactsRepository.getWhatsAppContacts { current, total ->
                        // Update progress on main thread
                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            loadingProgress = current
                            loadingTotal = total
                        }
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isLoadingContacts = false

                        if (allWhatsAppContacts.isNotEmpty()) {
                            showContactPicker = true
                            Toast.makeText(context, "Loaded ${allWhatsAppContacts.size} WhatsApp contacts", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No WhatsApp contacts found.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isLoadingContacts = false
                        Toast.makeText(context, "Error loading contacts: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Read Contacts permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = uri.lastPathSegment?.lowercase() ?: ""
            val contacts = when {
                mimeType == "text/csv" || fileName.endsWith(".csv") -> contactsRepository.parseCsv(context, uri)
                mimeType == "text/x-vcard" || fileName.endsWith(".vcf") -> contactsRepository.parseVcf(context, uri)
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || fileName.endsWith(".xlsx") -> contactsRepository.parseXlsx(uri)
                mimeType == "text/comma-separated-values" -> contactsRepository.parseCsv(context, uri)
                else -> {
                    Toast.makeText(context, "Unsupported file type. Please select CSV files.", Toast.LENGTH_LONG).show()
                    emptyList()
                }
            }
            if (contacts.isNotEmpty()) {
                importedContacts = contacts
                
                // Check if we're adding to existing group or creating new
                if (selectedGroupForAddition != null) {
                    // Add to existing group directly
                    scope.launch {
                        try {
                            val result = contactsRepository.addContactsToGroup(selectedGroupForAddition!!, contacts)
                            result.fold(
                                onSuccess = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    selectedGroupForAddition = null
                                    importedContacts = emptyList()
                                },
                                onFailure = { error ->
                                    if (error.message?.contains("Contact limit reached") == true) {
                                        showPremiumDialog = true
                                    } else {
                                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                    }
                                    selectedGroupForAddition = null
                                }
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error adding contacts: ${e.message}", Toast.LENGTH_LONG).show()
                            selectedGroupForAddition = null
                        }
                    }
                } else {
                    // Create new group
                    showGroupNameDialog = true
                }
            } else if (contacts.isEmpty() && mimeType != null) {
                Toast.makeText(context, "No valid contacts found in the selected file.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importOptions = listOf(
        ImportOptionInfo(Icons.Default.Description, "CSV", MaterialTheme.colorScheme.primary) {
            filePickerLauncher.launch("*/*")
        },

        ImportOptionInfo(Icons.Default.TableView, "XLSX", MaterialTheme.colorScheme.tertiary) {
            filePickerLauncher.launch("*/*")
        },
        ImportOptionInfo(Icons.Default.GridOn, "Sheets", Color(0xFF34A853)) {
            showSheetsLinkDialog = true
        },
        ImportOptionInfo(Icons.Default.ContentPaste, "Text", Color(0xFFFF9800)) {
            showPasteTextDialog = true
        },
        ImportOptionInfo(Icons.Default.Contacts, "Phone", Color(0xFF00E676)) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) -> {
                    // Load contacts in background to prevent UI freeze
                    isLoadingContacts = true
                    Toast.makeText(context, "Loading contacts...", Toast.LENGTH_SHORT).show()

                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            totalDeviceContacts = contactsRepository.getTotalContactsCount()
                            allWhatsAppContacts = contactsRepository.getWhatsAppContacts { current, total ->
                                // Update progress on main thread
                                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    loadingProgress = current
                                    loadingTotal = total
                                }
                            }

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isLoadingContacts = false

                                if (allWhatsAppContacts.isNotEmpty()) {
                                    showContactPicker = true
                                    Toast.makeText(context, "Loaded ${allWhatsAppContacts.size} WhatsApp contacts", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No WhatsApp contacts found.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isLoadingContacts = false
                                Toast.makeText(context, "Error loading contacts: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                            else -> permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    )

    var selectedTab by remember { mutableStateOf(0) }
    
    // Separate groups into Lists and Folders
    val listGroups = savedGroups.filter { !it.name.contains("/") }
    val folderGroups = savedGroups.filter { it.name.contains("/") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Contact Groups",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    icon = { 
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Lists"
                        )
                    },
                    label = { Text("Lists") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    icon = { 
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Folders"
                        )
                    },
                    label = { Text("Folders") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ImportSourcesCard(importOptions = importOptions)
                }
                item {
                    Text(
                        if (selectedTab == 0) "My Lists" else "My Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                
                // Show content based on selected tab
                if (selectedTab == 0) {
                    // Lists Tab - Show only manually created groups
                    if (listGroups.isEmpty()) {
                        item {
                            EmptyState(
                                message = "No lists yet!",
                                subtitle = "Use the options above to import contacts and create your first list.",
                                icon = Icons.Default.List
                            )
                        }
                    } else {
                        items(listGroups, key = { it.id }) { group ->
                            ContactGroupCard(
                                group = group,
                                onDelete = {
                                    scope.launch {
                                        contactsRepository.deleteGroup(group.id)
                                    }
                                },
                                onContactDelete = { contact ->
                                    scope.launch {
                                        contactsRepository.deleteContactFromGroup(group.id, contact.number)
                                    }
                                },
                                onDownload = { format ->
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val fileName = group.name.replace(Regex("[^a-zA-Z0-9]"), "_")
                                        val result = when (format) {
                                            "csv" -> com.message.bulksend.utils.ContactExportHelper.exportToCSV(
                                                context, group.contacts, fileName
                                            )

                                            else -> null
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (result != null) {
                                                Toast.makeText(
                                                    context,
                                                    "✅ Saved to Downloads/BulkSend/\n${fileName}.${format}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "❌ Failed to export contacts",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onAddContacts = { groupId ->
                                    // Show import options when user wants to add contacts to existing group
                                    // We'll create a simple dialog to let user choose import method
                                    showAddContactsDialog = true
                                    selectedGroupForAddition = groupId
                                }
                            )
                        }
                    }
                } else {
                    // Folders Tab - Show only OneShot and Launch folders
                    if (folderGroups.isEmpty()) {
                        item {
                            EmptyState(
                                message = "No folders yet!",
                                subtitle = "Use OneShot or OneTime features to create batches and ranges.",
                                icon = Icons.Default.Folder
                            )
                        }
                    } else {
                        // Group by folder name
                        val groupedFolders = folderGroups.groupBy { group ->
                            group.name.split("/")[0]
                        }
                        
                        groupedFolders.forEach { (folderName, groupList) ->
                            item {
                                FolderCard(
                                    folderName = folderName,
                                    groups = groupList,
                                    onDelete = { groupId ->
                                        scope.launch {
                                            contactsRepository.deleteGroup(groupId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Loading indicator overlay with green progress bar
            if (isLoadingContacts) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .width(260.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Title
                            Text(
                                "Loading Contacts",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Progress count
                            if (loadingTotal > 0) {
                                Text(
                                    "$loadingProgress / $loadingTotal",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676) // Green color
                                )
                            }

                            // Green Linear Progress Bar
                            if (loadingTotal > 0) {
                                val progress = loadingProgress.toFloat() / loadingTotal.toFloat()
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        color = Color(0xFF00E676), // Green
                                        trackColor = Color(0xFF00E676).copy(alpha = 0.2f)
                                    )
                                    Text(
                                        "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF00E676)
                                    )
                                }
                            } else {
                                // Indeterminate progress when total is not known
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    color = Color(0xFF00E676),
                                    trackColor = Color(0xFF00E676).copy(alpha = 0.2f)
                                )
                            }

                            Text(
                                "Please wait...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        WhatsAppContactPickerScreen(
            totalDeviceContacts = totalDeviceContacts,
            contacts = allWhatsAppContacts,
            onDismiss = { 
                showContactPicker = false
                // Reset selected group if user cancels
                if (selectedGroupForAddition != null) {
                    selectedGroupForAddition = null
                }
            },
            onImport = { selectedContacts ->
                showContactPicker = false
                if (selectedContacts.isNotEmpty()) {
                    importedContacts = selectedContacts
                    
                    // Check if we're adding to existing group or creating new
                    if (selectedGroupForAddition != null) {
                        // Add to existing group directly
                        scope.launch {
                            try {
                                val result = contactsRepository.addContactsToGroup(selectedGroupForAddition!!, selectedContacts)
                                result.fold(
                                    onSuccess = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        selectedGroupForAddition = null
                                        importedContacts = emptyList()
                                    },
                                    onFailure = { error ->
                                        if (error.message?.contains("Contact limit reached") == true) {
                                            showPremiumDialog = true
                                        } else {
                                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                        }
                                        selectedGroupForAddition = null
                                    }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error adding contacts: ${e.message}", Toast.LENGTH_LONG).show()
                                selectedGroupForAddition = null
                            }
                        }
                    } else {
                        // Create new group
                        showGroupNameDialog = true
                    }
                }
            }
        )
    }

    if (showGroupNameDialog) {
        GroupNameDialog(
            contactCount = importedContacts.size,
            onDismiss = { showGroupNameDialog = false },
            onConfirm = { groupName ->
                scope.launch {
                    ContactLimitHandler.saveGroupWithLimitCheck(
                        context = context,
                        repository = contactsRepository,
                        groupName = groupName,
                        contacts = importedContacts,
                        onSuccess = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            showGroupNameDialog = false
                            importedContacts = emptyList()
                        },
                        onLimitReached = { current, limit ->
                            currentContactCount = current
                            contactLimit = limit
                            showGroupNameDialog = false
                            showPremiumDialog = true
                        },
                        onPartialSave = { saved, skipped ->
                            Toast.makeText(
                                context,
                                "✅ Saved $saved contacts\n⚠️ Skipped $skipped contacts (limit reached)\n\n💎 Upgrade to Premium for unlimited!",
                                Toast.LENGTH_LONG
                            ).show()
                            showGroupNameDialog = false
                            importedContacts = emptyList()
                        }
                    )
                }
            },
            onAddToExisting = { groupId ->
                scope.launch {
                    try {
                        val result = contactsRepository.addContactsToGroup(groupId, importedContacts)
                        result.fold(
                            onSuccess = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                showGroupNameDialog = false
                                importedContacts = emptyList()
                            },
                            onFailure = { error ->
                                if (error.message?.contains("Contact limit reached") == true) {
                                    // Extract current count and limit from error message if possible
                                    showGroupNameDialog = false
                                    showPremiumDialog = true
                                } else {
                                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error adding contacts: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            existingGroups = savedGroups
        )
    }

    if (showPasteTextDialog) {
        PasteTextDialog(
            onDismiss = { 
                showPasteTextDialog = false
                // Reset selected group if user cancels
                if (selectedGroupForAddition != null) {
                    selectedGroupForAddition = null
                }
            },
            onConfirm = { text ->
                val contacts = contactsRepository.parseCommaSeparatedText(text)
                if (contacts.isNotEmpty()) {
                    importedContacts = contacts
                    showPasteTextDialog = false
                    
                    // Check if we're adding to existing group or creating new
                    if (selectedGroupForAddition != null) {
                        // Add to existing group directly
                        scope.launch {
                            try {
                                val result = contactsRepository.addContactsToGroup(selectedGroupForAddition!!, contacts)
                                result.fold(
                                    onSuccess = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        selectedGroupForAddition = null
                                        importedContacts = emptyList()
                                    },
                                    onFailure = { error ->
                                        if (error.message?.contains("Contact limit reached") == true) {
                                            showPremiumDialog = true
                                        } else {
                                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                        }
                                        selectedGroupForAddition = null
                                    }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error adding contacts: ${e.message}", Toast.LENGTH_LONG).show()
                                selectedGroupForAddition = null
                            }
                        }
                    } else {
                        // Create new group
                        showGroupNameDialog = true
                    }
                } else {
                    Toast.makeText(context, "No valid contacts found", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSheetsLinkDialog) {
        SheetsLinkDialog(
            onDismiss = { 
                showSheetsLinkDialog = false
                // Reset selected group if user cancels
                if (selectedGroupForAddition != null) {
                    selectedGroupForAddition = null
                }
            },
            onConfirm = { link ->
                scope.launch {
                    try {
                        val contacts = contactsRepository.fetchFromGoogleSheets(link)
                        if (contacts.isNotEmpty()) {
                            importedContacts = contacts
                            showSheetsLinkDialog = false
                            
                            // Check if we're adding to existing group or creating new
                            if (selectedGroupForAddition != null) {
                                // Add to existing group directly
                                try {
                                    val result = contactsRepository.addContactsToGroup(selectedGroupForAddition!!, contacts)
                                    result.fold(
                                        onSuccess = { message ->
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            selectedGroupForAddition = null
                                            importedContacts = emptyList()
                                        },
                                        onFailure = { error ->
                                            if (error.message?.contains("Contact limit reached") == true) {
                                                showPremiumDialog = true
                                            } else {
                                                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                            }
                                            selectedGroupForAddition = null
                                        }
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error adding contacts: ${e.message}", Toast.LENGTH_LONG).show()
                                    selectedGroupForAddition = null
                                }
                            } else {
                                // Create new group
                                showGroupNameDialog = true
                            }
                        } else {
                            Toast.makeText(context, "No valid contacts found in the sheet", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error fetching from Google Sheets: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Premium Dialog - Shows when contact limit is reached
    if (showPremiumDialog) {
        PremiumUpgradeDialog(
            currentContacts = currentContactCount,
            contactsLimit = contactLimit,
            onDismiss = {
                showPremiumDialog = false
                importedContacts = emptyList()
            },
            onUpgrade = {
                try {
                    val intent = Intent(context, UserProfileActivity::class.java)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Admin panel not available", Toast.LENGTH_SHORT).show()
                }
                showPremiumDialog = false
                importedContacts = emptyList()
            }
        )
    }

    // Add Contacts to Existing Group Dialog
    if (showAddContactsDialog && selectedGroupForAddition != null) {
        val selectedGroup = savedGroups.find { it.id == selectedGroupForAddition }
        AddContactsToGroupDialog(
            groupName = selectedGroup?.name ?: "Unknown Group",
            onDismiss = { 
                showAddContactsDialog = false 
                selectedGroupForAddition = null
            },
            onImportMethod = { method ->
                showAddContactsDialog = false
                // Handle different import methods for adding to existing group
                when (method) {
                    "csv" -> filePickerLauncher.launch("*/*")
                    "xlsx" -> filePickerLauncher.launch("*/*")
                    "sheets" -> showSheetsLinkDialog = true
                    "text" -> showPasteTextDialog = true
                    "phone" -> {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) -> {
                                // Load contacts in background
                                isLoadingContacts = true
                                Toast.makeText(context, "Loading contacts...", Toast.LENGTH_SHORT).show()

                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        totalDeviceContacts = contactsRepository.getTotalContactsCount()
                                        allWhatsAppContacts = contactsRepository.getWhatsAppContacts { current, total ->
                                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                                loadingProgress = current
                                                loadingTotal = total
                                            }
                                        }

                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            isLoadingContacts = false
                                            if (allWhatsAppContacts.isNotEmpty()) {
                                                showContactPicker = true
                                                Toast.makeText(context, "Loaded ${allWhatsAppContacts.size} WhatsApp contacts", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No WhatsApp contacts found.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            isLoadingContacts = false
                                            Toast.makeText(context, "Error loading contacts: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                            else -> permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ImportSourcesCard(importOptions: List<ImportOptionInfo>) {
    // Create a vibrant gradient for the card background
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    // Create a vibrant gradient for the border
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                brush = borderGradient,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .background(cardGradient)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Import Contacts From",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // New icon layout with improved design
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    importOptions.take(3).forEach { option ->
                        ImportOption(
                            icon = option.icon,
                            label = option.label,
                            color = option.color,
                            onClick = option.onClick
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    importOptions.drop(3).forEach { option ->
                        ImportOption(
                            icon = option.icon,
                            label = option.label,
                            color = option.color,
                            onClick = option.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImportOption(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Create a gradient for the icon background
    val iconGradient = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = 0.3f),
            color.copy(alpha = 0.1f)
        ),
        center = Offset(50f, 50f),
        radius = 100f
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .scale(scale)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(iconGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ContactGroupCard(
    group: Group,
    onDelete: () -> Unit,
    onContactDelete: (Contact) -> Unit = {},
    onDownload: (format: String) -> Unit = {},
    onAddContacts: ((Long) -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "rotation"
    )
    
    // Check if this is a OneShot batch group
    val isOneShotBatch = group.name.contains("/")
    val (folderName, batchName) = if (isOneShotBatch) {
        val parts = group.name.split("/")
        Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
    } else {
        Pair("", group.name)
    }
    
    // Extract batch number for color assignment
    val batchNumber = if (isOneShotBatch) {
        batchName.filter { it.isDigit() }.toIntOrNull() ?: 0
    } else {
        0
    }
    
    // Color palette for OneShot batches
    val batchColors = listOf(
        Color(0xFF7C4DFF), // Purple
        Color(0xFF00E676), // Green
        Color(0xFF00B0FF), // Blue
        Color(0xFFFF6E40), // Orange
        Color(0xFFFF4081), // Pink
        Color(0xFFFFD740), // Yellow
        Color(0xFF00E5FF), // Cyan
        Color(0xFF76FF03), // Lime
        Color(0xFFE040FB), // Magenta
        Color(0xFF40C4FF)  // Light Blue
    )
    
    val accentColor = if (isOneShotBatch && batchNumber > 0) {
        batchColors[(batchNumber - 1) % batchColors.size]
    } else {
        MaterialTheme.colorScheme.secondary
    }

    // Create a gradient for the card
    val cardGradient = Brush.linearGradient(
        colors = if (isOneShotBatch) {
            listOf(
                MaterialTheme.colorScheme.surface,
                accentColor.copy(alpha = 0.1f)
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant
            )
        },
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isOneShotBatch) 2.dp else 1.dp,
            color = if (isOneShotBatch) accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.background(cardGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create a gradient background for the icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.3f),
                                        accentColor.copy(alpha = 0.1f)
                                    ),
                                    center = Offset(20f, 20f),
                                    radius = 40f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOneShotBatch) {
                            // Show batch number
                            Text(
                                text = batchNumber.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = accentColor
                            )
                        } else {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Add Contacts button (only for manual groups, not OneShot batches)
                    if (!isOneShotBatch && onAddContacts != null) {
                        IconButton(onClick = { onAddContacts(group.id) }) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Add Contacts",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Download button
                    IconButton(onClick = { onDownload("csv") }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download CSV",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            // Add Contacts option (only for manual groups)
                            if (!isOneShotBatch && onAddContacts != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Add Contacts",
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onAddContacts(group.id)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = "Add Contacts",
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Download as CSV",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onDownload("csv")
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = "CSV",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete Group",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            modifier = Modifier.rotate(rotationAngle),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isOneShotBatch) {
                        // Show folder name as subtitle
                        ScrollingGroupTitle(
                            title = folderName,
                            fontSize = 11.sp,
                            minFontSize = 9.sp,
                            color = accentColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                        ScrollingGroupTitle(
                            title = batchName,
                            fontSize = 16.sp,
                            minFontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        ScrollingGroupTitle(
                            title = group.name,
                            fontSize = 16.sp,
                            minFontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${group.contacts.size} contacts",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                GroupContactsSection(
                    group = group,
                    onContactDelete = onContactDelete
                )
            }
        }
    }

}

@Composable
fun ContactListItem(
    contact: Contact,
    onDeleteClick: (() -> Unit)? = null
) {
    // Create a gradient for the contact item
    val itemGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        ),
        start = Offset.Zero,
        end = Offset(300f, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemGradient)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Create a gradient background for the icon
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        center = Offset(12f, 12f),
                        radius = 24f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PersonPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.name,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                contact.number,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        // Delete icon
        if (onDeleteClick != null) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Contact",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun GroupContactsSection(
    group: Group,
    onContactDelete: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }
    
    // Filter contacts based on search query
    val filteredContacts = remember(group.contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            group.contacts
        } else {
            group.contacts.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                contact.number.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search contacts...", fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        
        // Contact count info
        if (searchQuery.isNotEmpty()) {
            Text(
                "Found ${filteredContacts.size} of ${group.contacts.size} contacts",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        if (filteredContacts.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                items(filteredContacts) { contact ->
                    ContactListItem(
                        contact = contact,
                        onDeleteClick = { contactToDelete = contact }
                    )
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            Text(
                "No contacts found matching \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "This group is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
            )
        }
    }
    
    // Delete confirmation dialog
    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "Delete Contact",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete this contact from the group?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    contactToDelete!!.name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    contactToDelete!!.number,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onContactDelete(contactToDelete!!)
                        contactToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyState(message: String, subtitle: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp)
    ) {
        // Create a gradient background for the icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        ),
                        center = Offset(32f, 32f),
                        radius = 64f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Empty State",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            message,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GroupNameDialog(
    contactCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onAddToExisting: ((Long) -> Unit)? = null,
    existingGroups: List<Group> = emptyList()
) {
    var groupName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Create New, 1 = Add to Existing
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }

    // Create a gradient for the dialog background
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedTab == 0) "Create New Group" else "Add to Existing Group",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.background(dialogGradient)
            ) {
                Text(
                    "$contactCount contacts found.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Show tabs only if there are existing groups and onAddToExisting is provided
                if (existingGroups.isNotEmpty() && onAddToExisting != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Tab Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedTab = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create New", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { selectedTab = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add to Existing", fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (selectedTab == 0) {
                    // Create New Group Tab
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name", color = MaterialTheme.colorScheme.onSurface) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                } else {
                    // Add to Existing Group Tab
                    Text(
                        "Select a group to add contacts:",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Filter out OneShot/Launch folders - only show manually created groups
                    val manualGroups = existingGroups.filter { !it.name.contains("/") }
                    
                    if (manualGroups.isEmpty()) {
                        Text(
                            "No manual groups available. OneShot/Launch folders cannot be modified.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(manualGroups) { group ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            selectedGroupId = if (selectedGroupId == group.id) null else group.id 
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedGroupId == group.id) 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (selectedGroupId == group.id) 
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                                    else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            tint = if (selectedGroupId == group.id) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            ScrollingGroupTitle(
                                                title = group.name,
                                                fontSize = 14.sp,
                                                minFontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "${group.contacts.size} contacts",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (selectedGroupId == group.id) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (selectedTab == 0) {
                        onConfirm(groupName)
                    } else {
                        selectedGroupId?.let { groupId ->
                            onAddToExisting?.invoke(groupId)
                        }
                    }
                },
                enabled = if (selectedTab == 0) groupName.isNotBlank() else selectedGroupId != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (selectedTab == 0) "Create" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun PasteTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    // Create a gradient for the dialog background
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Paste Contacts",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.background(dialogGradient)
            ) {
                Text(
                    "Paste comma-separated text. Each line should contain a name and number (e.g., John, 1234567890).",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Paste here", color = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun SheetsLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var link by remember { mutableStateOf("") }

    // Create a gradient for the dialog background
    val dialogGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = null,
                    tint = Color(0xFF34A853),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Google Sheets Link",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.background(dialogGradient)
            ) {
                Text(
                    "Paste your Google Sheets link. Make sure the sheet is publicly accessible and has Name in column A and Phone Number in column B.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Google Sheets URL", color = MaterialTheme.colorScheme.onSurface) },
                    placeholder = { Text("https://docs.google.com/spreadsheets/d/...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(link) },
                enabled = link.isNotBlank() && link.contains("docs.google.com/spreadsheets"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34A853),
                    contentColor = Color.White
                )
            ) {
                Text("Fetch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppContactPickerScreen(
    totalDeviceContacts: Int,
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onImport: (List<Contact>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Use phone number as key for better performance with large lists
    // Start with NO contacts selected - user must explicitly select what they want
    var selectedContactNumbers by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Sort contacts by name and filter by search query with partial matching
    val filteredContacts = remember(searchQuery, contacts) {
        val sorted = contacts.sortedBy { it.name.lowercase() }
        if (searchQuery.isBlank()) {
            sorted
        } else {
            // Partial name matching - matches any part of the name
            // Example: "Raj" matches "Raj", "Rajesh", "Suraj", "Rajiv"
            sorted.filter { contact ->
                val query = searchQuery.trim().lowercase()
                val name = contact.name.lowercase()

                // Check if any word in name starts with query OR contains query
                name.contains(query) ||
                        name.split(" ").any { word -> word.startsWith(query) }
            }
        }
    }

    val isAllSelected = remember(selectedContactNumbers, filteredContacts, searchQuery) {
        if (filteredContacts.isEmpty()) {
            false
        } else {
            // Check if all filtered contacts are selected
            filteredContacts.all { it.number in selectedContactNumbers }
        }
    }

    // Create a gradient for the top bar
    val topBarGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Select WhatsApp Contacts",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Green Import Button with analytics card style
                    Button(
                        onClick = {
                            val selected = contacts.filter { it.number in selectedContactNumbers }
                            onImport(selected)
                        },
                        enabled = selectedContactNumbers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676), // Green
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF00E676).copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp,
                            disabledElevation = 0.dp
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Import",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Import (${selectedContactNumbers.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ContactSummaryCard(
                totalDeviceContacts = totalDeviceContacts,
                whatsAppContactsCount = contacts.size,
                filteredCount = if (searchQuery.isNotBlank()) filteredContacts.size else null
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search by name...", color = MaterialTheme.colorScheme.onSurface) },
                    placeholder = { Text("e.g. Raj, Amit", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { checked ->
                            android.util.Log.d("ContactPicker", "All checkbox changed: $checked, searchQuery: '$searchQuery', filteredCount: ${filteredContacts.size}")

                            if (searchQuery.isBlank()) {
                                // If no search, select/deselect ALL contacts
                                selectedContactNumbers = if (checked) {
                                    contacts.map { c -> c.number }.toSet()
                                } else {
                                    emptySet()
                                }
                                android.util.Log.d("ContactPicker", "Selected count after change: ${selectedContactNumbers.size}")
                            } else {
                                // If searching, select/deselect only filtered contacts
                                selectedContactNumbers = if (checked) {
                                    selectedContactNumbers + filteredContacts.map { c -> c.number }
                                } else {
                                    selectedContactNumbers - filteredContacts.map { c -> c.number }.toSet()
                                }
                                android.util.Log.d("ContactPicker", "Selected count after filtered change: ${selectedContactNumbers.size}")
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Text(
                        if (searchQuery.isNotBlank()) "Filtered" else "All",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    count = filteredContacts.size,
                    key = { index -> filteredContacts[index].number }
                ) { index ->
                    val contact = filteredContacts[index]
                    ContactPickerItem(
                        contact = contact,
                        isSelected = contact.number in selectedContactNumbers,
                        onSelectionChange = {
                            selectedContactNumbers = if (contact.number in selectedContactNumbers) {
                                selectedContactNumbers - contact.number
                            } else {
                                selectedContactNumbers + contact.number
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactPickerItem(
    contact: Contact,
    isSelected: Boolean,
    onSelectionChange: () -> Unit
) {
    // Create a gradient for the contact item
    val itemGradient = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
        ),
        start = Offset.Zero,
        end = Offset(300f, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemGradient)
            .clickable(onClick = onSelectionChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onSelectionChange() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.width(16.dp))
        // Show only name in list (number hidden for cleaner UI)
        Text(
            contact.name,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollingGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    minFontSize: TextUnit = 12.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val adjustedFontSize = when {
        title.length >= 40 -> minFontSize
        title.length >= 28 -> (fontSize.value - 2).coerceAtLeast(minFontSize.value).sp
        title.length >= 20 -> (fontSize.value - 1).coerceAtLeast(minFontSize.value).sp
        else -> fontSize
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE),
            fontSize = adjustedFontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun ContactSummaryCard(totalDeviceContacts: Int, whatsAppContactsCount: Int, filteredCount: Int? = null) {
    // Create a gradient for the card
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .background(cardGradient)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = if (filteredCount != null) Arrangement.SpaceEvenly else Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                count = totalDeviceContacts,
                label = "Total Contacts",
                icon = Icons.Default.Contacts
            )
            SummaryItem(
                count = whatsAppContactsCount,
                label = "On WhatsApp",
                icon = Icons.Default.Chat
            )
            // Show filtered count when searching
            if (filteredCount != null) {
                SummaryItem(
                    count = filteredCount,
                    label = "Filtered",
                    icon = Icons.Default.FilterList
                )
            }
        }
    }
}

@Composable
fun SummaryItem(count: Int, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Create a gradient background for the icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        center = Offset(24f, 24f),
                        radius = 48f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun FolderCard(
    folderName: String,
    groups: List<Group>,
    onDelete: (Long) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Sort groups by batch number
    val sortedGroups = groups.sortedBy { group ->
        val batchName = group.name.split("/").getOrNull(1) ?: ""
        batchName.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
    
    // Calculate total contacts
    val totalContacts = sortedGroups.sumOf { it.contacts.size }
    
    // Color palette for batches
    val batchColors = listOf(
        Color(0xFF7C4DFF), Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFFF6E40),
        Color(0xFFFF4081), Color(0xFFFFD740), Color(0xFF00E5FF), Color(0xFF76FF03),
        Color(0xFFE040FB), Color(0xFF40C4FF)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Folder Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    ScrollingGroupTitle(
                        title = folderName,
                        fontSize = 16.sp,
                        minFontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${sortedGroups.size} batches • $totalContacts contacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Expanded content - show all batches
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedGroups.forEachIndexed { index, group ->
                        val batchName = group.name.split("/").getOrNull(1) ?: ""
                        val batchNumber = batchName.filter { it.isDigit() }.toIntOrNull() ?: (index + 1)
                        val accentColor = batchColors[(batchNumber - 1) % batchColors.size]
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = accentColor.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Batch number circle
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(accentColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = batchNumber.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = accentColor
                                        )
                                    }

                                    Spacer(Modifier.weight(1f))

                                    // Delete button
                                    IconButton(
                                        onClick = { onDelete(group.id) }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ScrollingGroupTitle(
                                        title = batchName,
                                        fontSize = 14.sp,
                                        minFontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${group.contacts.size} contacts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddContactsToGroupDialog(
    groupName: String,
    onDismiss: () -> Unit,
    onImportMethod: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add Contacts to Group",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column {
                Text(
                    "Adding contacts to: \"$groupName\"",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Choose how to import contacts:",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Import options
                val importOptions = listOf(
                    Pair("CSV File", "csv"),
                    Pair("Excel File", "xlsx"),
                    Pair("Google Sheets", "sheets"),
                    Pair("Paste Text", "text"),
                    Pair("Phone Contacts", "phone")
                )
                
                importOptions.forEach { (label, method) ->
                    TextButton(
                        onClick = { onImportMethod(method) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (method) {
                                "csv" -> Icons.Default.Description
                                "xlsx" -> Icons.Default.TableView
                                "sheets" -> Icons.Default.GridOn
                                "text" -> Icons.Default.ContentPaste
                                "phone" -> Icons.Default.Contacts
                                else -> Icons.Default.Add
                            }
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                label,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Preview(showBackground = true)
@Composable
fun ManageContactsScreenPreview() {
    ContactsTheme {
        ManageContactsScreen()
    }
}



