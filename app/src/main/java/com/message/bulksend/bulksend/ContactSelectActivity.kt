package com.message.bulksend.bulksend

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.contactmanager.Group
import com.message.bulksend.components.CountryCodeSelector
import com.message.bulksend.utils.Country
import com.message.bulksend.utils.CountryCodeManager
import com.message.bulksend.utils.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactSelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isForSelection = intent.getBooleanExtra("IS_FOR_SELECTION", false)
        val callingActivity = intent.getStringExtra("CALLING_ACTIVITY")
        val targetActivity = intent.getStringExtra("TARGET_ACTIVITY")
        
        setContent {
            ContactSelectTheme {
                ContactSelectScreen(
                    isForSelection = isForSelection,
                    callingActivity = callingActivity,
                    targetActivity = targetActivity
                )
            }
        }
    }
}

private val autonomousExtraKeys =
    listOf(
        "AUTONOMOUS_MESSAGE",
        "AUTONOMOUS_UNIQUE",
        "AUTONOMOUS_DAYS",
        "AUTONOMOUS_RISK_SCORE"
    )

private fun resolveTargetActivityClass(targetActivity: String?): Class<*> {
    return when (targetActivity) {
        "BulktextActivity",
        "AutonomousTextActivity" -> Class.forName("com.message.bulksend.bulksend.textcamp.BulktextActivity")
        "TextmediaActivity" -> Class.forName("com.message.bulksend.bulksend.textmedia.TextmediaActivity")
        else -> BulksendActivity::class.java
    }
}

private fun Intent.copyAutonomousExtrasFrom(source: Intent?) {
    if (source == null) return
    autonomousExtraKeys.forEach { key ->
        if (source.hasExtra(key)) {
            when (val value = source.extras?.get(key)) {
                is String -> putExtra(key, value)
                is Boolean -> putExtra(key, value)
                is Int -> putExtra(key, value)
                is Long -> putExtra(key, value)
                is Float -> putExtra(key, value)
                is Double -> putExtra(key, value)
            }
        }
    }
}

@Composable
fun ContactSelectTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF00C853),
        primaryContainer = Color(0xFF00E676),
        secondary = Color(0xFF2979FF),
        surface = Color(0xFF1A1A2E),
        surfaceVariant = Color(0xFF16213E),
        background = Color(0xFF0F0F1E),
        onPrimary = Color.White,
        onSurface = Color(0xFFE0E0FF),
        onBackground = Color(0xFFE0E0FF),
        outline = Color(0xFF3F51B5),
        error = Color(0xFFFF5252)
    )

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = colors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(colorScheme = colors) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectScreen(
    isForSelection: Boolean = false,
    callingActivity: String? = null,
    targetActivity: String? = null
) {
    val context = LocalContext.current
    val sourceIntent = remember { context.findActivity()?.intent }
    val contactsRepository = remember { ContactsRepository(context) }
    
    val groups by contactsRepository.loadGroups().collectAsState(initial = emptyList())
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var selectedContacts by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var manualNumbers by remember { mutableStateOf("") }
    var manuallyAddedContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    
    // Campaign details
    var campaignName by remember { mutableStateOf("") }
    // Auto-detect country from SIM if user hasn't selected one
    var selectedCountry by remember { mutableStateOf(CountryCodeManager.getOrAutoDetectCountry(context)) }
    
    // Group name dialog
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    
    val selectionPrefs = remember {
        context.getSharedPreferences("contact_select_state", android.content.Context.MODE_PRIVATE)
    }
    val campaignHistoryPrefs = remember {
        context.getSharedPreferences("campaign_selection_history", android.content.Context.MODE_PRIVATE)
    }
    var excludeUsedNumbers by remember { mutableStateOf(true) }
    var usedNumbersForGroup by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filteredContacts = remember(selectedGroup, searchQuery, excludeUsedNumbers, usedNumbersForGroup) {
        selectedGroup?.contacts?.filter { contact ->
            val matchesSearch =
                contact.name.contains(searchQuery, ignoreCase = true) ||
                    contact.number.contains(searchQuery, ignoreCase = true)
            val passesUsedFilter = !excludeUsedNumbers || contact.number !in usedNumbersForGroup
            matchesSearch && passesUsedFilter
        } ?: emptyList()
    }
    var restoredSelectionHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedGroup?.id) {
        val group = selectedGroup ?: run {
            restoredSelectionHint = null
            usedNumbersForGroup = emptySet()
            return@LaunchedEffect
        }

        val usedKey = "used_${group.id}"
        val usedSet = campaignHistoryPrefs.getStringSet(usedKey, emptySet())?.toSet() ?: emptySet()
        usedNumbersForGroup = usedSet

        val savedKey = "selected_${group.id}"
        val lastIndexKey = "last_index_${group.id}"
        val savedSet = selectionPrefs.getStringSet(savedKey, emptySet())?.toSet() ?: emptySet()
        var validSavedSet = savedSet.filter { number -> group.contacts.any { it.number == number } }.toSet()
        if (excludeUsedNumbers && usedSet.isNotEmpty()) {
            validSavedSet = validSavedSet - usedSet
        }

        if (selectedContacts != validSavedSet) {
            selectedContacts = validSavedSet
        }

        val savedLastIndex = selectionPrefs.getInt(lastIndexKey, -1)
        restoredSelectionHint = if (validSavedSet.isNotEmpty() && savedLastIndex in group.contacts.indices) {
            val lastContact = group.contacts[savedLastIndex]
            "Previous selection restored: ${validSavedSet.size} contacts | last at ${savedLastIndex + 1}/${group.contacts.size} (${lastContact.name})"
        } else if (validSavedSet.isNotEmpty()) {
            "Previous selection restored: ${validSavedSet.size} contacts"
        } else {
            null
        }
    }

    LaunchedEffect(selectedGroup?.id, excludeUsedNumbers, usedNumbersForGroup) {
        if (excludeUsedNumbers && usedNumbersForGroup.isNotEmpty()) {
            selectedContacts = selectedContacts - usedNumbersForGroup
        }
    }

    LaunchedEffect(selectedGroup?.id, selectedContacts) {
        val group = selectedGroup ?: return@LaunchedEffect
        val validSelected = selectedContacts.filter { number -> group.contacts.any { it.number == number } }.toSet()
        if (validSelected != selectedContacts) {
            selectedContacts = validSelected
            return@LaunchedEffect
        }

        val selectedKey = "selected_${group.id}"
        val lastIndexKey = "last_index_${group.id}"
        val lastSelectedIndex = group.contacts.indexOfLast { it.number in validSelected }
        val editor = selectionPrefs.edit().putStringSet(selectedKey, HashSet(validSelected))
        if (lastSelectedIndex >= 0) {
            editor.putInt(lastIndexKey, lastSelectedIndex)
        } else {
            editor.remove(lastIndexKey)
        }
        editor.apply()
    }

    val currentSelectionHint = remember(selectedGroup, selectedContacts) {
        val group = selectedGroup ?: return@remember null
        val lastSelectedIndex = group.contacts.indexOfLast { it.number in selectedContacts }
        if (lastSelectedIndex in group.contacts.indices) {
            val lastContact = group.contacts[lastSelectedIndex]
            "Selected till: ${lastSelectedIndex + 1}/${group.contacts.size} (${lastContact.name})"
        } else {
            null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Select Contacts",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (selectedGroup != null) {
                        Button(
                            onClick = {
                                if (selectedContacts.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Please select at least one contact",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                
                                // If in selection mode, return selected contacts
                                if (isForSelection) {
                                    val group = selectedGroup!!
                                    val selectedContactsList = group.contacts.filter {
                                        selectedContacts.contains(it.number) 
                                    }
                                    val usedKey = "used_${group.id}"
                                    val existingUsed = campaignHistoryPrefs.getStringSet(usedKey, emptySet())?.toSet() ?: emptySet()
                                    val updatedUsed = existingUsed + selectedContactsList.map { it.number }
                                    campaignHistoryPrefs.edit().putStringSet(usedKey, HashSet(updatedUsed)).apply()
                                    usedNumbersForGroup = updatedUsed

                                    selectionPrefs.edit()
                                        .remove("selected_${group.id}")
                                        .remove("last_index_${group.id}")
                                        .apply()
                                    selectedContacts = emptySet()

                                    val resultIntent = Intent().apply {
                                        putStringArrayListExtra("SELECTED_CONTACTS", ArrayList(selectedContactsList.map { it.number }))
                                        putStringArrayListExtra("SELECTED_NAMES", ArrayList(selectedContactsList.map { it.name }))
                                        putExtra("TOTAL_CONTACTS", selectedContactsList.size)
                                    }
                                    (context as? Activity)?.setResult(Activity.RESULT_OK, resultIntent)
                                    (context as? Activity)?.finish()
                                    return@Button
                                }
                                
                                // Normal mode validations
                                if (campaignName.isBlank()) {
                                    Toast.makeText(context, "Please enter campaign name", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (selectedCountry == null) {
                                    Toast.makeText(context, "Please select country code", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Launch target activity directly with selected contacts (no temp group creation).
                                val group = selectedGroup!!
                                val selectedContactsList = group.contacts.filter {
                                    selectedContacts.contains(it.number)
                                }
                                val usedKey = "used_${group.id}"
                                val existingUsed = campaignHistoryPrefs.getStringSet(usedKey, emptySet())?.toSet() ?: emptySet()
                                val updatedUsed = existingUsed + selectedContactsList.map { it.number }
                                campaignHistoryPrefs.edit().putStringSet(usedKey, HashSet(updatedUsed)).apply()
                                usedNumbersForGroup = updatedUsed

                                selectionPrefs.edit()
                                    .remove("selected_${group.id}")
                                    .remove("last_index_${group.id}")
                                    .apply()
                                selectedContacts = emptySet()

                                val targetClass = resolveTargetActivityClass(targetActivity)
                                val launchIntent = Intent(context, targetClass).apply {
                                    putStringArrayListExtra("SELECTED_CONTACTS", ArrayList(selectedContactsList.map { it.number }))
                                    putStringArrayListExtra("SELECTED_NAMES", ArrayList(selectedContactsList.map { it.name }))
                                    putExtra("SELECTED_GROUP_NAME", group.name)
                                    putExtra("FROM_CONTACT_SELECT", true)
                                    putExtra("CAMPAIGN_NAME", campaignName)
                                    putExtra("COUNTRY_CODE", selectedCountry?.dial_code ?: "")
                                    putExtra("TOTAL_CONTACTS", selectedContactsList.size)
                                    copyAutonomousExtrasFrom(sourceIntent)
                                }
                                context.startActivity(launchIntent)
                                (context as? Activity)?.finish()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Next",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Next (${selectedContacts.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Campaign Setup Section (Hide in selection mode)
            if (!isForSelection) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Campaign Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Campaign Name
                        OutlinedTextField(
                            value = campaignName,
                            onValueChange = { campaignName = it },
                            label = { Text("Campaign Name") },
                            placeholder = { Text("e.g., Summer Sale 2025") },
                            leadingIcon = {
                                Icon(Icons.Default.Campaign, contentDescription = "Campaign")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        // Country Code Selector
                        CountryCodeSelector(
                            selectedCountry = selectedCountry,
                            onCountrySelected = { country ->
                                selectedCountry = country
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Group Selector with two options
            if (selectedGroup == null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Select a Group",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Choose a group to send campaign",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            
                            // Add New Contacts Button
                            IconButton(
                                onClick = {
                                    val intent = Intent(context, com.message.bulksend.contactmanager.ContactzActivity::class.java)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add Contacts",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    if (groups.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "No groups found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Create a group first to select contacts",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        // Group by folder for OneShot batches and OneTime ranges
                        val groupedItems = groups.groupBy { group ->
                            if (group.name.contains("/")) {
                                group.name.split("/")[0] // Folder name (OneShot folder or "OneTime")
                            } else {
                                group.name // Individual group
                            }
                        }
                        
                        groupedItems.forEach { (folderName, groupList) ->
                            if (groupList.size >= 1 && groupList.first().name.contains("/")) {
                                // Groups in folder (OneShot batches or OneTime ranges) - show as collapsible card
                                item {
                                    FolderGroupCard(
                                        folderName = folderName,
                                        groups = groupList,
                                        campaignName = campaignName,
                                        selectedCountry = selectedCountry,
                                        context = context,
                                        targetActivity = targetActivity,
                                        onGroupSelected = { selectedGroup = it }
                                    )
                                }
                            } else {
                                // Single group or non-folder group
                                items(groupList) { group ->
                                    GroupCardWithActions(
                                        group = group,
                                        campaignName = campaignName,
                                        selectedCountry = selectedCountry,
                                        targetActivity = targetActivity,
                                        onSelectAll = {
                                    // Check if launched for selection mode
                                    if (isForSelection) {
                                        // Return selected group to calling activity
                                        val resultIntent = Intent().apply {
                                            putExtra("SELECTED_GROUP_ID", group.id.toString())
                                            putExtra("GROUP_NAME", group.name)
                                            putExtra("TOTAL_CONTACTS", group.contacts.size)
                                        }
                                        (context as? Activity)?.setResult(Activity.RESULT_OK, resultIntent)
                                        (context as? Activity)?.finish()
                                        return@GroupCardWithActions
                                    }
                                    
                                    // Validation for normal mode
                                    if (campaignName.isBlank()) {
                                        Toast.makeText(context, "Please enter campaign name", Toast.LENGTH_SHORT).show()
                                        return@GroupCardWithActions
                                    }
                                    if (selectedCountry == null) {
                                        Toast.makeText(context, "Please select country code", Toast.LENGTH_SHORT).show()
                                        return@GroupCardWithActions
                                    }
                                    
                                    // Navigate to target activity (BulksendActivity, BulktextActivity, or TextmediaActivity)
                                    val targetClass = resolveTargetActivityClass(targetActivity)
                                    val intent = Intent(context, targetClass).apply {
                                        putExtra("SELECTED_GROUP_ID", group.id.toString())
                                        putExtra("FROM_CONTACT_SELECT", false)
                                        putExtra("CAMPAIGN_NAME", campaignName)
                                        putExtra("COUNTRY_CODE", selectedCountry?.dial_code ?: "")
                                        copyAutonomousExtrasFrom(sourceIntent)
                                    }
                                    context.startActivity(intent)
                                    (context as? Activity)?.finish()
                                },
                                onSelectSpecific = {
                                    // In selection mode, just select the group for contact picking
                                    if (isForSelection) {
                                        selectedGroup = group
                                        return@GroupCardWithActions
                                    }
                                    
                                    // Validation for normal mode
                                    if (campaignName.isBlank()) {
                                        Toast.makeText(context, "Please enter campaign name", Toast.LENGTH_SHORT).show()
                                        return@GroupCardWithActions
                                    }
                                    if (selectedCountry == null) {
                                        Toast.makeText(context, "Please select country code", Toast.LENGTH_SHORT).show()
                                        return@GroupCardWithActions
                                    }
                                    
                                    // Contact selection screen khulega
                                    selectedGroup = group
                                },
                                onOneTime = {
                                    // Validation
                                    if (campaignName.isBlank()) {
                                        Toast.makeText(context, "Please enter campaign name", Toast.LENGTH_SHORT).show()
                                        return@GroupCardWithActions
                                    }
                                    if (selectedCountry == null) {
                                        Toast.makeText(context, "Please select country code", Toast.LENGTH_SHORT).show()
                                        return@GroupCardWithActions
                                    }
                                    
                                    // OneTime range selection activity khulega
                                    val intent = Intent(context, OneTimeActivity::class.java).apply {
                                        putExtra("GROUP_ID", group.id.toString())
                                        putExtra("GROUP_NAME", group.name)
                                        putExtra("TOTAL_CONTACTS", group.contacts.size)
                                        putExtra("CAMPAIGN_NAME", campaignName)
                                        putExtra("COUNTRY_CODE", selectedCountry?.dial_code ?: "")
                                        putExtra("TARGET_ACTIVITY", targetActivity)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                                }
                            }
                        }
                    }
                }
            } else {
                // Contact Selection
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Selected Group Header
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        selectedGroup!!.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${selectedGroup!!.contacts.size} contacts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                TextButton(onClick = {
                                    selectedGroup = null
                                    selectedContacts = emptySet()
                                }) {
                                    Text("Change", color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }

                    item {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            placeholder = { Text("Search contacts...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    item {
                        // Select All / Deselect All
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${selectedContacts.size} selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        selectedContacts = if (searchQuery.isBlank()) {
                                            filteredContacts.map { it.number }.toSet()
                                        } else {
                                            selectedContacts + filteredContacts.map { it.number }
                                        }
                                    }
                                ) {
                                    Text("Select All", color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(
                                    onClick = {
                                        selectedContacts = if (searchQuery.isBlank()) {
                                            emptySet()
                                        } else {
                                            selectedContacts - filteredContacts.map { it.number }.toSet()
                                        }
                                    }
                                ) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (usedNumbersForGroup.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Used in previous campaign: ${usedNumbersForGroup.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        if (excludeUsedNumbers) "Excluded from contact list" else "Included in contact list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Switch(
                                    checked = excludeUsedNumbers,
                                    onCheckedChange = { excludeUsedNumbers = it }
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        val removedCount = selectedContacts.count { it in usedNumbersForGroup }
                                        selectedContacts = selectedContacts - usedNumbersForGroup
                                        if (removedCount > 0) {
                                            Toast.makeText(context, "Removed $removedCount used contacts from selection", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = selectedContacts.any { it in usedNumbersForGroup }
                                ) {
                                    Text("Remove Used")
                                }

                                TextButton(
                                    onClick = {
                                        val groupId = selectedGroup!!.id
                                        campaignHistoryPrefs.edit().remove("used_$groupId").apply()
                                        usedNumbersForGroup = emptySet()
                                        Toast.makeText(context, "Used history reset", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Reset Used History", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (restoredSelectionHint != null || currentSelectionHint != null) {
                        item {
                            val hintText = currentSelectionHint ?: restoredSelectionHint
                            if (hintText != null) {
                                Text(
                                    text = hintText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }

                    items(filteredContacts) { contact ->
                        ContactItem(
                            contact = contact,
                            isSelected = selectedContacts.contains(contact.number),
                            onToggle = {
                                selectedContacts = if (selectedContacts.contains(contact.number)) {
                                    selectedContacts - contact.number
                                } else {
                                    selectedContacts + contact.number
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (filteredContacts.isEmpty()) {
                        item {
                            Text(
                                text = if (excludeUsedNumbers && usedNumbersForGroup.isNotEmpty()) {
                                    "No contacts left after excluding previously used numbers."
                                } else {
                                    "No contacts found."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Group Name Dialog
    GroupNameDialog(
        showDialog = showGroupNameDialog,
        onDismiss = { showGroupNameDialog = false },
        groupName = newGroupName,
        onGroupNameChange = { newGroupName = it },
        selectedContactsCount = selectedContacts.size,
        context = context,
        onConfirm = {
            showGroupNameDialog = false
            
            // Filter selected contacts from the group
            val filteredContacts = selectedGroup!!.contacts.filter { 
                selectedContacts.contains(it.number) 
            }
            
            // Save to repository with user-provided name
            val contactsRepo = ContactsRepository(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                val result = contactsRepo.saveGroup(newGroupName, filteredContacts)
                
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        // Get the saved group ID by finding the most recent group
                        CoroutineScope(Dispatchers.IO).launch {
                            val groups = contactsRepo.loadGroups()
                            var savedGroupId: Long? = null
                            
                            groups.collect { groupList ->
                                savedGroupId = groupList.find { it.name == newGroupName }?.id
                                
                                withContext(Dispatchers.Main) {
                                    if (savedGroupId != null) {
                                        // Navigate to target activity
                                        val targetClass = resolveTargetActivityClass(targetActivity)
                                        val intent = Intent(context, targetClass).apply {
                                            putExtra("SELECTED_GROUP_ID", savedGroupId.toString())
                                            putExtra("FROM_CONTACT_SELECT", true)
                                            putExtra("CAMPAIGN_NAME", campaignName)
                                            putExtra("COUNTRY_CODE", selectedCountry?.dial_code ?: "")
                                            copyAutonomousExtrasFrom(sourceIntent)
                                        }
                                        context.startActivity(intent)
                                        (context as? Activity)?.finish()
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Error: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    )
}

@Composable
fun GroupCardWithActions(
    group: Group,
    onSelectAll: () -> Unit,
    onSelectSpecific: () -> Unit,
    onOneTime: () -> Unit,
    campaignName: String = "",
    selectedCountry: Country? = null,
    targetActivity: String? = null
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("group_notes", android.content.Context.MODE_PRIVATE) }
    
    var showInfoDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(sharedPrefs.getString("note_${group.id}", "") ?: "") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Group Info with Help Button
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${group.contacts.size} contacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Notes Button
                IconButton(
                    onClick = { showNotesDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (noteText.isNotEmpty()) Icons.Default.Edit else Icons.Default.Description,
                        contentDescription = "Notes",
                        tint = if (noteText.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Info Button
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Show note if exists
            if (noteText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            noteText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Action Buttons - Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Select Specific Button (now first position - left)
                OutlinedButton(
                    onClick = onSelectSpecific,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Select", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
                
                // Continue Button (now second position - right)
                Button(
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // OneTime Button
            OutlinedButton(
                onClick = onOneTime,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFFFF6E40)),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF6E40)
                )
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("OneTime", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    
    // Help Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "How to Use",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 500.dp)
                ) {
                    item {
                        HelpSection(
                            title = "📋 Select",
                            description = "Choose specific contacts from this group",
                            steps = listOf(
                                "Click 'Select' button",
                                "Pick individual contacts you want",
                                "Click 'Next' to proceed with selected contacts"
                            ),
                            benefit = "Perfect when you need only certain contacts from the group"
                        )
                    }
                    
                    item {
                        HelpSection(
                            title = "➡️ Continue",
                            description = "Use all contacts in this group",
                            steps = listOf(
                                "Click 'Continue' button",
                                "All ${group.contacts.size} contacts will be selected",
                                "Proceed directly to campaign"
                            ),
                            benefit = "Quick way to send to everyone in the group"
                        )
                    }
                    
                    item {
                        HelpSection(
                            title = "⏰ OneTime",
                            description = "Select specific ranges of contacts",
                            steps = listOf(
                                "Click 'OneTime' button",
                                "Set contacts per range (e.g., 200)",
                                "Select ranges you want (1-200, 201-400, etc.)",
                                "Selected range contacts open directly in campaign"
                            ),
                            benefit = "Send to specific portions. Example: Send to contacts 1-200 today, 201-400 tomorrow"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Got it!")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
    
    // Notes Dialog
    if (showNotesDialog) {
        NotesDialog(
            showDialog = showNotesDialog,
            onDismiss = { showNotesDialog = false },
            noteText = noteText,
            onNoteTextChange = { noteText = it },
            onSave = {
                sharedPrefs.edit().putString("note_${group.id}", noteText).apply()
                showNotesDialog = false
                Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                noteText = ""
                sharedPrefs.edit().remove("note_${group.id}").apply()
                showNotesDialog = false
                Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun HelpSection(
    title: String,
    description: String,
    steps: List<String>,
    benefit: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                "Steps:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    benefit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(Modifier.width(12.dp))
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    contact.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            if (contact.isWhatsApp) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "WhatsApp",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun GroupNameDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    selectedContactsCount: Int,
    onConfirm: () -> Unit,
    context: android.content.Context
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "Save Selected Contacts",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a name for this group of $selectedContactsCount selected contacts:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = onGroupNameChange,
                        label = { Text("Group Name") },
                        placeholder = { Text("e.g., Selected Contacts") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isBlank()) {
                            Toast.makeText(context, "Please enter group name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}


@Composable
fun FolderGroupCard(
    folderName: String,
    groups: List<Group>,
    campaignName: String,
    selectedCountry: Country?,
    context: android.content.Context,
    targetActivity: String?,
    onGroupSelected: (Group) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var groupToDelete by remember { mutableStateOf<Group?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    
    val contactsRepository = remember { ContactsRepository(context) }
    val scope = rememberCoroutineScope()
    
    // SharedPreferences for folder notes
    val sharedPrefs = remember { context.getSharedPreferences("folder_notes", android.content.Context.MODE_PRIVATE) }
    var noteText by remember { mutableStateOf(sharedPrefs.getString("folder_note_$folderName", "") ?: "") }
    
    // Sort groups by batch number
    val sortedGroups = groups.sortedBy { group ->
        val batchName = group.name.split("/").getOrNull(1) ?: ""
        batchName.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
    
    // Filter groups based on search
    val filteredGroups = if (searchQuery.isBlank()) {
        sortedGroups
    } else {
        sortedGroups.filter { group ->
            group.name.contains(searchQuery, ignoreCase = true) ||
            group.contacts.size.toString().contains(searchQuery)
        }
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Folder Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded },
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
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            folderName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${sortedGroups.size} batches • $totalContacts contacts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Notes Button
                IconButton(
                    onClick = { showNotesDialog = true }
                ) {
                    Icon(
                        if (noteText.isNotEmpty()) Icons.Default.Note else Icons.Default.NoteAdd,
                        contentDescription = "Notes",
                        tint = if (noteText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Note Preview (if note exists)
            if (noteText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Note,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            noteText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Expanded content - show search bar and batches
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search batches...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    // Show filtered count if searching
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            "${filteredGroups.size} of ${sortedGroups.size} batches found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    filteredGroups.forEachIndexed { index, group ->
                        val batchName = group.name.split("/").getOrNull(1) ?: ""
                        val batchNumber = batchName.filter { it.isDigit() }.toIntOrNull() ?: (index + 1)
                        val accentColor = batchColors[(batchNumber - 1) % batchColors.size]
                        
                        BatchCard(
                            group = group,
                            batchName = batchName,
                            batchNumber = batchNumber,
                            accentColor = accentColor,
                            campaignName = campaignName,
                            selectedCountry = selectedCountry,
                            context = context,
                            targetActivity = targetActivity,
                            onGroupSelected = onGroupSelected,
                            onDelete = {
                                groupToDelete = group
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                groupToDelete = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Delete Batch?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to delete this batch?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                groupToDelete!!.name.split("/").getOrNull(1) ?: groupToDelete!!.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${groupToDelete!!.contacts.size} contacts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Text(
                        "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            contactsRepository.deleteGroup(groupToDelete!!.id)
                            Toast.makeText(
                                context,
                                "Batch deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            showDeleteDialog = false
                            groupToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    groupToDelete = null
                }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
    
    // Folder Notes Dialog
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📁",
                        fontSize = 24.sp
                    )
                    Text(
                        "Folder Notes",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Add notes for folder: $folderName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = { Text("e.g., Important campaign, Send on weekends, etc.") },
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    if (noteText.isNotEmpty()) {
                        Text(
                            "${noteText.length} characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (noteText.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                noteText = ""
                                sharedPrefs.edit().remove("folder_note_$folderName").apply()
                                showNotesDialog = false
                                Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                    
                    Button(
                        onClick = {
                            sharedPrefs.edit().putString("folder_note_$folderName", noteText).apply()
                            showNotesDialog = false
                            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun BatchCard(
    group: Group,
    batchName: String,
    batchNumber: Int,
    accentColor: Color,
    campaignName: String,
    selectedCountry: Country?,
    context: android.content.Context,
    targetActivity: String?,
    onGroupSelected: (Group) -> Unit,
    onDelete: () -> Unit
) {
    val sharedPrefs = remember { context.getSharedPreferences("group_notes", android.content.Context.MODE_PRIVATE) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(sharedPrefs.getString("note_${group.id}", "") ?: "") }
    
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
            // Batch header with notes and delete buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        batchName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${group.contacts.size} contacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Notes Button
                IconButton(
                    onClick = { showNotesDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (noteText.isNotEmpty()) Icons.Default.Edit else Icons.Default.Description,
                        contentDescription = "Notes",
                        tint = if (noteText.isNotEmpty()) MaterialTheme.colorScheme.secondary else accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Show note if exists
            if (noteText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            noteText,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Select Button
                OutlinedButton(
                    onClick = {
                        if (campaignName.isBlank()) {
                            Toast.makeText(context, "Please enter campaign name", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        if (selectedCountry == null) {
                            Toast.makeText(context, "Please select country code", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        
                        onGroupSelected(group)
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, accentColor),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Select", fontSize = 12.sp, color = accentColor)
                }
                
                // Continue Button
                Button(
                    onClick = {
                        if (campaignName.isBlank()) {
                            Toast.makeText(context, "Please enter campaign name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedCountry == null) {
                            Toast.makeText(context, "Please select country code", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        // Use targetActivity to launch correct activity
                        val targetClass = resolveTargetActivityClass(targetActivity)
                        val intent = Intent(context, targetClass).apply {
                            putExtra("SELECTED_GROUP_ID", group.id.toString())
                            putExtra("FROM_CONTACT_SELECT", false)
                            putExtra("CAMPAIGN_NAME", campaignName)
                            putExtra("COUNTRY_CODE", selectedCountry.dial_code)
                            copyAutonomousExtrasFrom(context.findActivity()?.intent)
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Continue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    // Notes Dialog
    if (showNotesDialog) {
        NotesDialog(
            showDialog = showNotesDialog,
            onDismiss = { showNotesDialog = false },
            noteText = noteText,
            onNoteTextChange = { noteText = it },
            onSave = {
                sharedPrefs.edit().putString("note_${group.id}", noteText).apply()
                showNotesDialog = false
                Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                noteText = ""
                sharedPrefs.edit().remove("note_${group.id}").apply()
                showNotesDialog = false
                Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun NotesDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    noteText: String,
    onNoteTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📝",
                        fontSize = 24.sp
                    )
                    Text(
                        "Group Notes",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Add notes to remember important details about this group:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = onNoteTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = { Text("e.g., VIP customers, Send on weekends only, etc.") },
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    if (noteText.isNotEmpty()) {
                        Text(
                            "${noteText.length} characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (noteText.isNotEmpty()) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                    
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
