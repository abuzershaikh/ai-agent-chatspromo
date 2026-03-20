package com.message.bulksend.bulksend

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.utils.findActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OneTimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val groupId = intent.getStringExtra("GROUP_ID") ?: ""
        val groupName = intent.getStringExtra("GROUP_NAME") ?: ""
        val totalContacts = intent.getIntExtra("TOTAL_CONTACTS", 0)
        val campaignName = intent.getStringExtra("CAMPAIGN_NAME") ?: ""
        val countryCode = intent.getStringExtra("COUNTRY_CODE") ?: ""
        val targetActivity = intent.getStringExtra("TARGET_ACTIVITY") ?: "BulksendActivity"
        
        setContent {
            OneTimeTheme {
                OneTimeScreen(groupId, groupName, totalContacts, campaignName, countryCode, targetActivity)
            }
        }
    }
}

@Composable
fun OneTimeTheme(content: @Composable () -> Unit) {
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
fun OneTimeScreen(
    groupId: String,
    groupName: String,
    totalContacts: Int,
    campaignName: String,
    countryCode: String,
    targetActivity: String = "BulksendActivity"
) {
    val context = LocalContext.current
    
    // Get OneShot limit from SharedPreferences (default 200)
    val sharedPrefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    var oneShotLimit by remember { mutableStateOf(sharedPrefs.getInt("one_shot_limit", 200)) }

    // Persist progress for continuity: "up to which contact index one-time send was prepared"
    val progressPrefs = context.getSharedPreferences("one_time_progress", android.content.Context.MODE_PRIVATE)
    val progressKey = remember(groupId) { "last_end_$groupId" }
    var lastCreatedEnd by remember(groupId) { mutableStateOf(progressPrefs.getInt(progressKey, 0)) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    // Calculate ranges based on limit
    val ranges = remember(totalContacts, oneShotLimit) {
        val rangeList = mutableListOf<Pair<Int, Int>>()
        var start = 1
        while (start <= totalContacts) {
            val end = minOf(start + oneShotLimit - 1, totalContacts)
            rangeList.add(Pair(start, end))
            start = end + 1
        }
        rangeList
    }

    // Show only remaining ranges after last created end, so user continues from where they stopped.
    val availableRanges = remember(ranges, lastCreatedEnd) {
        ranges.filter { (start, _) -> start > lastCreatedEnd }
    }
    val remainingContacts = remember(totalContacts, lastCreatedEnd) {
        (totalContacts - lastCreatedEnd).coerceAtLeast(0)
    }
    
    var selectedRanges by remember { mutableStateOf(setOf<Int>()) }
    var expanded by remember { mutableStateOf(false) }
    val selectedRangePairsPreview = remember(selectedRanges, availableRanges) {
        selectedRanges
            .sorted()
            .mapNotNull { availableRanges.getOrNull(it) }
    }
    val selectedRangePreviewText = remember(selectedRangePairsPreview) {
        when {
            selectedRangePairsPreview.isEmpty() -> ""
            selectedRangePairsPreview.size <= 2 -> {
                selectedRangePairsPreview.joinToString(", ") { (start, end) -> "$start-$end" }
            }
            else -> {
                val head = selectedRangePairsPreview
                    .take(2)
                    .joinToString(", ") { (start, end) -> "$start-$end" }
                "$head +${selectedRangePairsPreview.size - 2} more"
            }
        }
    }

    fun launchCampaignWithContacts(contactNumbers: List<String>, contactNames: List<String>, groupLabel: String) {
        if (contactNumbers.isEmpty()) {
            Toast.makeText(context, "No contacts available", Toast.LENGTH_SHORT).show()
            return
        }

        val safeNames = contactNumbers.mapIndexed { index, number ->
            contactNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: number
        }

        val targetClass = when (targetActivity) {
            "BulktextActivity" -> Class.forName("com.message.bulksend.bulksend.textcamp.BulktextActivity")
            "TextmediaActivity" -> Class.forName("com.message.bulksend.bulksend.textmedia.TextmediaActivity")
            else -> BulksendActivity::class.java
        }

        val intent = Intent(context, targetClass).apply {
            putStringArrayListExtra("SELECTED_CONTACTS", ArrayList(contactNumbers))
            putStringArrayListExtra("SELECTED_NAMES", ArrayList(safeNames))
            putExtra("SELECTED_GROUP_NAME", groupLabel)
            putExtra("FROM_CONTACT_SELECT", true)
            putExtra("CAMPAIGN_NAME", campaignName)
            putExtra("COUNTRY_CODE", countryCode)
            putExtra("TOTAL_CONTACTS", contactNumbers.size)
        }
        context.startActivity(intent)
        (context as? Activity)?.finish()
    }

    LaunchedEffect(availableRanges) {
        selectedRanges = selectedRanges.filter { it in availableRanges.indices }.toSet()
        if (availableRanges.isEmpty()) {
            expanded = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "OneTime Range Selection",
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
                    TextButton(onClick = { showResetDialog = true }) {
                        Text(
                            "Reset",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group Info Card
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "$totalContacts contacts total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        "Processed till end number: $lastCreatedEnd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        "Remaining contacts: $remainingContacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    // Contacts Per Range Input Field
                    OutlinedTextField(
                        value = oneShotLimit.toString(),
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                val limit = newValue.toIntOrNull() ?: 200
                                if (limit > 0) {
                                    oneShotLimit = limit
                                    sharedPrefs.edit().putInt("one_shot_limit", limit).apply()
                                    selectedRanges = emptySet() // Reset selection when limit changes
                                }
                            }
                        },
                        label = { Text("Contacts Per Range") },
                        placeholder = { Text("e.g., 200") },
                        leadingIcon = {
                            Icon(Icons.Default.Group, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "OneTime Continuity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (availableRanges.isNotEmpty()) {
                        Text(
                            "Next range starts from: ${availableRanges.first().first}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            "All contacts are already covered in one-time progress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Range Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    if (availableRanges.isNotEmpty()) {
                        expanded = !expanded
                    }
                }
            ) {
                OutlinedTextField(
                    value = if (availableRanges.isEmpty()) {
                        "No remaining ranges to send"
                    } else if (selectedRangePairsPreview.isEmpty()) {
                        "Select One Time Range"
                    } else {
                        selectedRangePreviewText
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select One Time Range") },
                    trailingIcon = {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Dropdown"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = availableRanges.isNotEmpty(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .heightIn(max = 400.dp)
                ) {
                    availableRanges.forEachIndexed { index, (start, end) ->
                        val isSelected = selectedRanges.contains(index)
                        
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "$start - $end",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            },
                            onClick = {
                                selectedRanges = if (isSelected) {
                                    selectedRanges - index
                                } else {
                                    selectedRanges + index
                                }
                            }
                        )
                    }
                }
            }
            
            // Selected Ranges Summary
            if (selectedRangePairsPreview.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Selected Ranges",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        val totalSelected = selectedRangePairsPreview.sumOf { (start, end) ->
                            end - start + 1
                        }
                        
                        Text(
                            "$totalSelected Contacts Selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        selectedRangePairsPreview.forEach { (start, end) ->
                            Text(
                                "- $start - $end",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            
            // Continue Button
            Button(
                onClick = {
                    if (availableRanges.isEmpty()) {
                        Toast.makeText(context, "All ranges are already covered", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedRanges.isEmpty()) {
                        Toast.makeText(context, "Please select at least one range", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val selectedRangePairs = selectedRanges
                        .sorted()
                        .mapNotNull { availableRanges.getOrNull(it) }
                    if (selectedRangePairs.isEmpty()) {
                        Toast.makeText(context, "Selected ranges are no longer available", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    android.util.Log.d("OneTime", "Continue clicked - GroupId: $groupId")
                    android.util.Log.d("OneTime", "Selected ranges: $selectedRangePairs")
                    android.util.Log.d("OneTime", "Campaign: $campaignName, Country: $countryCode")
                    
                    val contactsRepo = ContactsRepository(context)
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Load the original group and build direct contact selection.
                            val allGroups = contactsRepo.loadGroups().first()
                            val originalGroup = allGroups.find { it.id.toString() == groupId }
                            
                            if (originalGroup == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Group not found", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            
                            android.util.Log.d("OneTime", "Original group: ${originalGroup.name} with ${originalGroup.contacts.size} contacts")

                            var latestProcessedEnd = lastCreatedEnd
                            val selectedContacts = mutableListOf<com.message.bulksend.contactmanager.Contact>()
                            
                            selectedRangePairs.forEach { (start, end) ->
                                for (i in start..end) {
                                    originalGroup.contacts.getOrNull(i - 1)?.let { contact ->
                                        selectedContacts.add(contact)
                                    }
                                }
                                latestProcessedEnd = maxOf(latestProcessedEnd, end)
                            }

                            val uniqueSelectedContacts = selectedContacts
                                .associateBy { it.number }
                                .values
                                .toList()

                            if (uniqueSelectedContacts.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "No contacts found for selected ranges", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            
                            withContext(Dispatchers.Main) {
                                lastCreatedEnd = latestProcessedEnd
                                progressPrefs.edit()
                                    .putInt(progressKey, latestProcessedEnd)
                                    .apply()
                                selectedRanges = emptySet()

                                val selectedNumbers = uniqueSelectedContacts.map { it.number }
                                val selectedNames = uniqueSelectedContacts.map { it.name }

                                Toast.makeText(
                                    context,
                                    "Selected ${uniqueSelectedContacts.size} contacts for campaign",
                                    Toast.LENGTH_SHORT
                                ).show()

                                launchCampaignWithContacts(
                                    contactNumbers = selectedNumbers,
                                    contactNames = selectedNames,
                                    groupLabel = originalGroup.name
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OneTime", "Error: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedRanges.isNotEmpty() && availableRanges.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text("Reset OneTime progress?")
            },
            text = {
                Text(
                    "This will reset continuity and start again from contact 1 for this group."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lastCreatedEnd = 0
                        selectedRanges = emptySet()
                        expanded = false
                        progressPrefs.edit()
                            .putInt(progressKey, 0)
                            .apply()
                        showResetDialog = false
                        Toast.makeText(
                            context,
                            "OneTime progress reset to start",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(
                        "Reset",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

