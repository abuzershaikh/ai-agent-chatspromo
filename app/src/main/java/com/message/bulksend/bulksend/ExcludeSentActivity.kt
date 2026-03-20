package com.message.bulksend.bulksend

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.utils.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * Activity to exclude already sent contacts from current campaign
 * 
 * Flow:
 * 1. User selects group/batch
 * 2. Shows preview of contacts with sent status
 * 3. User clicks "Exclude Sent" button
 * 4. Returns filtered contacts (only unsent) to calling activity
 */
class ExcludeSentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val groupId = intent.getStringExtra("GROUP_ID") ?: ""
        val groupName = intent.getStringExtra("GROUP_NAME") ?: ""
        val campaignName = intent.getStringExtra("CAMPAIGN_NAME") ?: ""
        val countryCode = intent.getStringExtra("COUNTRY_CODE") ?: ""
        val targetActivity = intent.getStringExtra("TARGET_ACTIVITY") ?: ""
        
        setContent {
            ExcludeSentTheme {
                ExcludeSentScreen(
                    groupId = groupId,
                    groupName = groupName,
                    campaignName = campaignName,
                    countryCode = countryCode,
                    targetActivity = targetActivity
                )
            }
        }
    }
}

@Composable
fun ExcludeSentTheme(content: @Composable () -> Unit) {
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

data class ContactWithSentStatus(
    val contact: Contact,
    val sentCampaigns: List<SentCampaignInfo>
)

data class SentCampaignInfo(
    val campaignName: String,
    val timestamp: Long,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludeSentScreen(
    groupId: String,
    groupName: String,
    campaignName: String,
    countryCode: String,
    targetActivity: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf<List<ContactWithSentStatus>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlySent by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<ContactWithSentStatus?>(null) }
    
    // Load contacts with sent status
    LaunchedEffect(groupId) {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val contactsRepo = ContactsRepository(context)
                    val database = AppDatabase.getInstance(context)
                    val campaignDao = database.campaignDao()
                    
                    android.util.Log.d("ExcludeSent", "Loading group: $groupId")
                    
                    // Load group contacts - use first() instead of collect
                    val groups = contactsRepo.loadGroups().first()
                    val group = groups.find { it.id.toString() == groupId }
                    
                    android.util.Log.d("ExcludeSent", "Group found: ${group?.name}, contacts: ${group?.contacts?.size}")
                    
                    if (group != null) {
                        // Get all campaigns
                        val allCampaigns = campaignDao.getAllCampaigns()
                        android.util.Log.d("ExcludeSent", "Total campaigns in DB: ${allCampaigns.size}")
                        
                        // Log first few campaigns for debugging
                        allCampaigns.take(3).forEach { campaign ->
                            android.util.Log.d("ExcludeSent", "Campaign: ${campaign.campaignName}, contacts: ${campaign.contactStatuses.size}, sent: ${campaign.sentCount}")
                        }
                        
                        // Map contacts with sent status
                        val contactsWithStatus = group.contacts.map { contact ->
                            // Try multiple number formats for matching
                            val originalNumber = contact.number
                            val cleanNumber = contact.number.replace(Regex("[^\\d]"), "") // Remove all non-digits
                            val withPlus = if (!contact.number.startsWith("+")) "+$cleanNumber" else contact.number
                            
                            android.util.Log.d("ExcludeSent", "Checking contact: ${contact.name}, original: $originalNumber, clean: $cleanNumber")
                            
                            // Find campaigns where this contact was sent
                            val sentCampaigns = allCampaigns.filter { campaign ->
                                val hasSent = campaign.contactStatuses.any { status ->
                                    // Try multiple matching strategies
                                    val statusOriginal = status.number
                                    val statusClean = status.number.replace(Regex("[^\\d]"), "")
                                    
                                    val isMatch = (
                                        statusOriginal == originalNumber ||
                                        statusOriginal == cleanNumber ||
                                        statusOriginal == withPlus ||
                                        statusClean == cleanNumber ||
                                        statusOriginal.endsWith(cleanNumber.takeLast(10)) ||
                                        cleanNumber.endsWith(statusClean.takeLast(10))
                                    ) && status.status == "sent"
                                    
                                    if (isMatch) {
                                        android.util.Log.d("ExcludeSent", "MATCH FOUND! Contact: $originalNumber matched with campaign status: $statusOriginal in campaign: ${campaign.campaignName}")
                                    }
                                    
                                    isMatch
                                }
                                
                                if (hasSent) {
                                    android.util.Log.d("ExcludeSent", "Campaign '${campaign.campaignName}' sent to ${contact.name}")
                                }
                                
                                hasSent
                            }.map { campaign ->
                                SentCampaignInfo(
                                    campaignName = campaign.campaignName,
                                    timestamp = campaign.timestamp,
                                    message = campaign.message.take(50) + if (campaign.message.length > 50) "..." else ""
                                )
                            }
                            
                            android.util.Log.d("ExcludeSent", "Contact ${contact.name}: ${sentCampaigns.size} campaigns sent")
                            
                            ContactWithSentStatus(contact, sentCampaigns)
                        }
                        
                        val sentCount = contactsWithStatus.count { it.sentCampaigns.isNotEmpty() }
                        android.util.Log.d("ExcludeSent", "Total contacts with sent campaigns: $sentCount")
                        
                        withContext(Dispatchers.Main) {
                            contacts = contactsWithStatus
                            isLoading = false
                        }
                    } else {
                        android.util.Log.e("ExcludeSent", "Group not found!")
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            Toast.makeText(context, "Group not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ExcludeSent", "Error loading data: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    // Filter contacts
    val filteredContacts = remember(contacts, searchQuery, showOnlySent) {
        contacts.filter { contactWithStatus ->
            val matchesSearch = contactWithStatus.contact.name.contains(searchQuery, ignoreCase = true) ||
                    contactWithStatus.contact.number.contains(searchQuery, ignoreCase = true)
            
            val matchesFilter = if (showOnlySent) {
                contactWithStatus.sentCampaigns.isNotEmpty()
            } else {
                true
            }
            
            matchesSearch && matchesFilter
        }
    }
    
    val sentCount = contacts.count { it.sentCampaigns.isNotEmpty() }
    val unsentCount = contacts.size - sentCount
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Exclude Already Sent",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            groupName,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard(
                            label = "Total",
                            value = contacts.size.toString(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatCard(
                            label = "Already Sent",
                            value = sentCount.toString(),
                            color = Color(0xFFFF6E40)
                        )
                        StatCard(
                            label = "Will Send",
                            value = unsentCount.toString(),
                            color = Color(0xFF00E676)
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Exclude Button
                    Button(
                        onClick = {
                            if (unsentCount == 0) {
                                Toast.makeText(
                                    context,
                                    "All contacts already received messages!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            
                            // Return only unsent contacts
                            val unsentContacts = contacts.filter { it.sentCampaigns.isEmpty() }
                            val resultIntent = Intent().apply {
                                putStringArrayListExtra(
                                    "FILTERED_NUMBERS",
                                    ArrayList(unsentContacts.map { it.contact.number })
                                )
                                putStringArrayListExtra(
                                    "FILTERED_NAMES",
                                    ArrayList(unsentContacts.map { it.contact.name })
                                )
                                putExtra("EXCLUDED_COUNT", sentCount)
                                putExtra("REMAINING_COUNT", unsentCount)
                            }
                            (context as? Activity)?.setResult(Activity.RESULT_OK, resultIntent)
                            (context as? Activity)?.finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = unsentCount > 0
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Exclude $sentCount & Continue with $unsentCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Search and Filter
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
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
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
                        
                        // Filter Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showOnlySent = !showOnlySent }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = showOnlySent,
                                onCheckedChange = { showOnlySent = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Show only contacts with sent messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                // Contacts List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredContacts) { contactWithStatus ->
                        ContactSentStatusCard(
                            contactWithStatus = contactWithStatus,
                            onClick = { selectedContact = contactWithStatus }
                        )
                    }
                    
                    if (filteredContacts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No contacts found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Contact Details Dialog
    selectedContact?.let { contact ->
        ContactDetailsDialog(
            contactWithStatus = contact,
            onDismiss = { selectedContact = null }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ContactSentStatusCard(
    contactWithStatus: ContactWithSentStatus,
    onClick: () -> Unit
) {
    val hasSent = contactWithStatus.sentCampaigns.isNotEmpty()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (hasSent) {
                Color(0xFFFF6E40).copy(alpha = 0.1f)
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
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasSent) Color(0xFFFF6E40).copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contactWithStatus.contact.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasSent) Color(0xFFFF6E40) else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            // Contact Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contactWithStatus.contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    contactWithStatus.contact.number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                if (hasSent) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFFF6E40),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "${contactWithStatus.sentCampaigns.size} campaign(s) sent",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF6E40),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Status Icon
            Icon(
                if (hasSent) Icons.Default.Block else Icons.Default.Send,
                contentDescription = null,
                tint = if (hasSent) Color(0xFFFF6E40) else Color(0xFF00E676),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ContactDetailsDialog(
    contactWithStatus: ContactWithSentStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contactWithStatus.contact.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        contactWithStatus.contact.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        contactWithStatus.contact.number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        text = {
            if (contactWithStatus.sentCampaigns.isEmpty()) {
                Text(
                    "No messages sent to this contact yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Sent Campaigns (${contactWithStatus.sentCampaigns.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(contactWithStatus.sentCampaigns) { campaign ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        campaign.campaignName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Text(
                                    campaign.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Text(
                                    java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                                        .format(java.util.Date(campaign.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
