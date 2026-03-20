package com.message.bulksend.autorespond.instastats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.message.bulksend.autorespond.database.MessageEntity
import com.message.bulksend.autorespond.database.MessageRepository
import com.message.bulksend.ui.theme.BulksendTestTheme
import java.text.SimpleDateFormat
import java.util.*

class InstagramMessageLogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                InstagramMessageLogsScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramMessageLogsScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val messageRepository = remember { MessageRepository(context) }
    
    // Get only Instagram messages
    val instagramMessages by messageRepository.getInstagramMessages().collectAsState(initial = emptyList())
    
    // Group messages by username
    val groupedMessages = remember(instagramMessages) {
        instagramMessages.groupBy { it.phoneNumber.removePrefix("instagram_") }
            .map { (username, messages) ->
                GroupedMessage(
                    username = username,
                    messages = messages.sortedByDescending { it.timestamp },
                    latestMessage = messages.maxByOrNull { it.timestamp } ?: messages.first()
                )
            }
            .sortedByDescending { it.latestMessage.timestamp }
    }
    
    // Track expanded rows
    var expandedRows by remember { mutableStateOf(setOf<String>()) }
    
    // Track selected rows for bulk operations
    var selectedRows by remember { mutableStateOf(setOf<String>()) }
    
    // Shared scroll state for synchronized horizontal scrolling
    val sharedScrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color(0xFF1a1a2e),
                shadowElevation = 4.dp
            ) {
                Column {
                    // Main Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Section - Back + Instagram Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = onBackPressed,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack, 
                                    contentDescription = "Back", 
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFE4405F),
                                                Color(0xFFF56040),
                                                Color(0xFFFCAF45)
                                            )
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "IG",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        // Right Section - Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Export Button
                            Surface(
                                color = Color(0xFF2D3748),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { /* Export functionality */ }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = "Export",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Export",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            // Stats Badge
                            Surface(
                                color = Color(0xFFE4405F).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    "Users: ${groupedMessages.size} | Messages: ${instagramMessages.size}",
                                    color = Color(0xFFE4405F),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    
                    // Secondary Header Row - Selection Info and Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left - Selection Info
                        if (selectedRows.isNotEmpty()) {
                            Text(
                                "${selectedRows.size} selected",
                                color = Color(0xFFE4405F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        
                        // Right - Filters and Sort
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Filter Button
                            Surface(
                                color = Color(0xFF2D3748),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { /* Filter functionality */ }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = "Filter",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Filter",
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            
                            // Sort Button
                            Surface(
                                color = Color(0xFF2D3748),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { /* Sort functionality */ }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Sort",
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF2D3748))
                    )
                }
            }
        },
        containerColor = Color(0xFF0f0f23)
    ) { padding ->
        if (groupedMessages.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = 16.dp, start = 32.dp, end = 32.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE4405F),
                                    Color(0xFFF56040),
                                    Color(0xFFFCAF45)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "IG",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "No Instagram Messages Yet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Instagram DM auto-replies will appear here once you start receiving messages",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Modern Spreadsheet Table with synchronized scrolling
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = 16.dp)
            ) {
                // Table Container with synchronized horizontal scroll
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0f0f23))
                ) {
                    Column {
                        // Fixed Header with shared scroll state
                        ModernTableHeader(
                            scrollState = sharedScrollState,
                            selectedRows = selectedRows,
                            allUsernames = groupedMessages.map { it.username }.toSet(),
                            onSelectAll = { isSelected ->
                                selectedRows = if (isSelected) {
                                    groupedMessages.map { it.username }.toSet()
                                } else {
                                    emptySet()
                                }
                            }
                        )
                        
                        // Scrollable Content with shared scroll state
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(groupedMessages) { index, groupedMessage ->
                                val isExpanded = expandedRows.contains(groupedMessage.username)
                                
                                ExpandableTableRow(
                                    groupedMessage = groupedMessage,
                                    index = index,
                                    isEven = index % 2 == 0,
                                    scrollState = sharedScrollState,
                                    isExpanded = isExpanded,
                                    isSelected = selectedRows.contains(groupedMessage.username),
                                    onToggleExpand = { username ->
                                        expandedRows = if (expandedRows.contains(username)) {
                                            expandedRows - username
                                        } else {
                                            expandedRows + username
                                        }
                                    },
                                    onToggleSelect = { username ->
                                        selectedRows = if (selectedRows.contains(username)) {
                                            selectedRows - username
                                        } else {
                                            selectedRows + username
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Data class for grouped messages
data class GroupedMessage(
    val username: String,
    val messages: List<MessageEntity>,
    val latestMessage: MessageEntity
)

@Composable
fun ModernTableHeader(
    scrollState: ScrollState,
    selectedRows: Set<String>,
    allUsernames: Set<String>,
    onSelectAll: (Boolean) -> Unit
) {
    val isAllSelected = selectedRows.containsAll(allUsernames) && allUsernames.isNotEmpty()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color(0xFF1a1a2e),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox Column
            TableHeaderCell(
                content = {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = onSelectAll,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFE4405F),
                            uncheckedColor = Color(0xFF64748B),
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                },
                width = 50.dp,
                isFirst = true
            )
            
            // Sr No Column
            TableHeaderCell(
                text = "Sr",
                width = 60.dp
            )
            
            // Username Column
            TableHeaderCell(
                text = "Username",
                width = 140.dp
            )
            
            // Incoming Message Column
            TableHeaderCell(
                text = "Incoming Message",
                width = 200.dp
            )
            
            // Reply Column
            TableHeaderCell(
                text = "Auto Reply",
                width = 200.dp
            )
            
            // Status Column
            TableHeaderCell(
                text = "Status",
                width = 100.dp
            )
            
            // Date Column
            TableHeaderCell(
                text = "Date",
                width = 100.dp
            )
            
            // Time Column
            TableHeaderCell(
                text = "Time",
                width = 80.dp,
                isLast = true
            )
        }
    }
}

@Composable
fun TableHeaderCell(
    text: String = "",
    content: (@Composable () -> Unit)? = null,
    width: Dp,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE4405F).copy(alpha = 0.1f),
                        Color(0xFF1a1a2e)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = Color(0xFF2D3748)
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE4405F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExpandableTableRow(
    groupedMessage: GroupedMessage,
    index: Int,
    isEven: Boolean,
    scrollState: ScrollState,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpand: (String) -> Unit,
    onToggleSelect: (String) -> Unit
) {
    val backgroundColor = if (isEven) Color(0xFF16213e) else Color(0xFF1a1a2e)
    val expandedHeight = if (isExpanded) 200.dp else 64.dp
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(expandedHeight),
        color = backgroundColor
    ) {
        Column {
            // Main Row (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox Cell
                TableCell(
                    content = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect(groupedMessage.username) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFE4405F),
                                uncheckedColor = Color(0xFF64748B),
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    width = 50.dp
                )
                
                // Sr No Cell (with expand/collapse functionality)
                TableCell(
                    content = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable { onToggleExpand(groupedMessage.username) }
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = Color(0xFFE4405F),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = (index + 1).toString(),
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    width = 60.dp
                )
                
                // Username Cell
                TableCell(
                    content = {
                        Column {
                            Text(
                                text = groupedMessage.username,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${groupedMessage.messages.size} messages",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    },
                    width = 140.dp
                )
                
                // Latest Message Cell
                TableCell(
                    content = {
                        Text(
                            text = groupedMessage.latestMessage.incomingMessage,
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    },
                    width = 200.dp
                )
                
                // Latest Reply Cell
                TableCell(
                    content = {
                        Text(
                            text = groupedMessage.latestMessage.outgoingMessage.ifEmpty { "No reply sent" },
                            fontSize = 12.sp,
                            color = if (groupedMessage.latestMessage.outgoingMessage.isNotEmpty()) Color(0xFF94A3B8) else Color(0xFF64748B),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                            fontStyle = if (groupedMessage.latestMessage.outgoingMessage.isEmpty()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                        )
                    },
                    width = 200.dp
                )
                
                // Status Cell
                TableCell(
                    content = {
                        val (statusColor, statusBg) = when (groupedMessage.latestMessage.status) {
                            "SENT" -> Color(0xFF10B981) to Color(0xFF10B981).copy(alpha = 0.15f)
                            "FAILED" -> Color(0xFFEF4444) to Color(0xFFEF4444).copy(alpha = 0.15f)
                            "PENDING" -> Color(0xFFF59E0B) to Color(0xFFF59E0B).copy(alpha = 0.15f)
                            else -> Color(0xFF64748B) to Color(0xFF64748B).copy(alpha = 0.15f)
                        }
                        
                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = groupedMessage.latestMessage.status,
                                fontSize = 11.sp,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    },
                    width = 100.dp
                )
                
                // Date Cell
                TableCell(
                    content = {
                        val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                        Text(
                            text = dateFormat.format(Date(groupedMessage.latestMessage.timestamp)),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    width = 100.dp
                )
                
                // Time Cell
                TableCell(
                    content = {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text(
                            text = timeFormat.format(Date(groupedMessage.latestMessage.timestamp)),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    width = 80.dp
                )
            }
            
            // Expanded Content (spreadsheet format with synchronized scroll)
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .background(Color(0xFF0f0f23).copy(alpha = 0.8f))
                ) {
                    // Sub-header for expanded content
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        color = Color(0xFF0f0f23),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Empty space for checkbox column
                            Box(modifier = Modifier.width(50.dp))
                            
                            // Empty space for Sr column with expand indicator
                            Box(modifier = Modifier.width(60.dp)) {
                                Text(
                                    "↳",
                                    fontSize = 16.sp,
                                    color = Color(0xFFE4405F),
                                    modifier = Modifier.padding(start = 20.dp)
                                )
                            }
                            
                            // Sub-headers matching main table
                            ExpandedTableHeaderCell("Time", 80.dp)
                            ExpandedTableHeaderCell("Message", 200.dp)
                            ExpandedTableHeaderCell("Reply", 200.dp)
                            ExpandedTableHeaderCell("Status", 100.dp)
                            ExpandedTableHeaderCell("Date", 100.dp)
                            ExpandedTableHeaderCell("Full Time", 80.dp)
                        }
                    }
                    
                    // Scrollable conversation rows with synchronized scroll
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(groupedMessage.messages) { msgIndex, message ->
                            ExpandedConversationRow(
                                message = message,
                                index = msgIndex,
                                scrollState = scrollState,
                                isEven = msgIndex % 2 == 0
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(
    content: @Composable () -> Unit,
    width: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(
                width = 0.5.dp,
                color = Color(0xFF2D3748)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

@Composable
fun ExpandedTableHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(32.dp)
            .background(Color(0xFF0f0f23))
            .border(
                width = 0.5.dp,
                color = Color(0xFF2D3748)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE4405F).copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExpandedConversationRow(
    message: MessageEntity,
    index: Int,
    scrollState: ScrollState,
    isEven: Boolean
) {
    val backgroundColor = if (isEven) Color(0xFF0a0a1a) else Color(0xFF0f0f23)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty space for checkbox column alignment
            Box(modifier = Modifier.width(50.dp))
            
            // Empty space for Sr column alignment
            Box(modifier = Modifier.width(60.dp))
            
            // Time Cell
            ExpandedTableCell(
                content = {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                },
                width = 80.dp
            )
            
            // Incoming Message Cell
            ExpandedTableCell(
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "📩",
                            fontSize = 12.sp
                        )
                        Text(
                            text = message.incomingMessage,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                },
                width = 200.dp
            )
            
            // Auto Reply Cell
            ExpandedTableCell(
                content = {
                    if (message.outgoingMessage.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "🤖",
                                fontSize = 12.sp
                            )
                            Text(
                                text = message.outgoingMessage,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 14.sp
                            )
                        }
                    } else {
                        Text(
                            text = "No reply sent",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                },
                width = 200.dp
            )
            
            // Status Cell
            ExpandedTableCell(
                content = {
                    val (statusColor, statusBg) = when (message.status) {
                        "SENT" -> Color(0xFF10B981) to Color(0xFF10B981).copy(alpha = 0.15f)
                        "FAILED" -> Color(0xFFEF4444) to Color(0xFFEF4444).copy(alpha = 0.15f)
                        "PENDING" -> Color(0xFFF59E0B) to Color(0xFFF59E0B).copy(alpha = 0.15f)
                        else -> Color(0xFF64748B) to Color(0xFF64748B).copy(alpha = 0.15f)
                    }
                    
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = message.status,
                            fontSize = 9.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                },
                width = 100.dp
            )
            
            // Date Cell
            ExpandedTableCell(
                content = {
                    val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                },
                width = 100.dp
            )
            
            // Full Time Cell
            ExpandedTableCell(
                content = {
                    val fullTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Text(
                        text = fullTimeFormat.format(Date(message.timestamp)),
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                },
                width = 80.dp
            )
        }
    }
}

@Composable
fun ExpandedTableCell(
    content: @Composable () -> Unit,
    width: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(
                width = 0.5.dp,
                color = Color(0xFF2D3748).copy(alpha = 0.5f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}