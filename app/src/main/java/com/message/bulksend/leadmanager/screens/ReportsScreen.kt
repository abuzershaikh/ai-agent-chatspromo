package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.model.FollowUp
import com.message.bulksend.leadmanager.customfields.CustomFieldsManager
import com.message.bulksend.leadmanager.customfields.model.CustomField
import com.message.bulksend.leadmanager.customfields.ui.getIconForFieldType
import com.message.bulksend.leadmanager.customfields.ui.getIconTintForFieldType
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.launch
import com.message.bulksend.leadmanager.utils.LeadExportHelper
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SortOption(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    STATUS("By Status")
}

// Available columns for customization
enum class ReportColumn(
    val displayName: String,
    val icon: ImageVector,
    val width: Int = 120,
    val category: ColumnCategory = ColumnCategory.BASIC
) {
    // Basic Lead Info
    LEAD_NAME("Lead Name", Icons.Default.Person, 150, ColumnCategory.BASIC),
    PHONE("Phone", Icons.Default.Phone, 140, ColumnCategory.BASIC),
    EMAIL("Email", Icons.Default.Email, 180, ColumnCategory.BASIC),
    STATUS("Status", Icons.Default.Flag, 120, ColumnCategory.BASIC),
    PRIORITY("Priority", Icons.Default.PriorityHigh, 100, ColumnCategory.BASIC),
    SOURCE("Source", Icons.Default.Source, 120, ColumnCategory.BASIC),
    CATEGORY("Category", Icons.Default.Category, 120, ColumnCategory.BASIC),
    
    // Date & Time
    CREATED_DATE("Created Date", Icons.Default.CalendarToday, 130, ColumnCategory.DATE),
    LAST_UPDATED("Last Updated", Icons.Default.Update, 130, ColumnCategory.DATE),
    
    // Follow-up Related
    NEXT_FOLLOWUP("Next Follow-up", Icons.Default.Schedule, 140, ColumnCategory.FOLLOWUP),
    FOLLOWUP_STATUS("Follow-up Status", Icons.Default.CheckCircle, 130, ColumnCategory.FOLLOWUP),
    FOLLOWUP_TYPE("Follow-up Type", Icons.Default.EventNote, 120, ColumnCategory.FOLLOWUP),
    FOLLOWUP_COUNT("Follow-up Count", Icons.Default.Numbers, 100, ColumnCategory.FOLLOWUP),
    
    // Product Related
    PRODUCT("Product", Icons.Default.Inventory, 150, ColumnCategory.PRODUCT),
    
    // Additional Info
    TAGS("Tags", Icons.Default.Label, 180, ColumnCategory.ADDITIONAL),
    NOTES("Notes", Icons.Default.Notes, 200, ColumnCategory.ADDITIONAL),
    LAST_MESSAGE("Last Message", Icons.Default.Message, 200, ColumnCategory.ADDITIONAL),
    LEAD_SCORE("Lead Score", Icons.Default.Score, 100, ColumnCategory.ADDITIONAL),
    
    // Contact Info
    COUNTRY_CODE("Country Code", Icons.Default.Public, 100, ColumnCategory.CONTACT),
    ALTERNATE_PHONE("Alt. Phone", Icons.Default.PhoneAndroid, 140, ColumnCategory.CONTACT)
}

enum class ColumnCategory(val displayName: String, val color: Long) {
    BASIC("Basic Info", 0xFF3B82F6),
    DATE("Date & Time", 0xFF10B981),
    FOLLOWUP("Follow-up", 0xFF8B5CF6),
    PRODUCT("Product", 0xFFF59E0B),
    ADDITIONAL("Additional", 0xFFEC4899),
    CONTACT("Contact", 0xFF06B6D4)
}


// Pagination constants
private const val PAGE_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(leads: List<Lead>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf<LeadStatus?>(null) }
    var selectedLeadId by remember { mutableStateOf<String?>(null) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    
    // Custom Fields
    val customFieldsManager = remember { CustomFieldsManager(context) }
    val customFields by customFieldsManager.getAllActiveFields().collectAsState(initial = emptyList())
    var customFieldValuesCache by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    
    // Load custom field values for all leads
    LaunchedEffect(leads, customFields) {
        if (customFields.isNotEmpty()) {
            val cache = mutableMapOf<String, Map<String, String>>()
            leads.forEach { lead ->
                cache[lead.id] = customFieldsManager.getValuesForLeadMap(lead.id)
            }
            customFieldValuesCache = cache
        }
    }
    
    // Pagination state
    var currentPage by remember { mutableStateOf(0) }
    
    // Selected columns - default columns
    var selectedColumns by remember { 
        mutableStateOf(
            listOf(
                ReportColumn.LEAD_NAME,
                ReportColumn.PHONE,
                ReportColumn.SOURCE,
                ReportColumn.STATUS,
                ReportColumn.LAST_UPDATED
            )
        ) 
    }
    
    // Selected custom field columns - Auto-select all custom fields
    var selectedCustomFieldIds by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Auto-add all custom fields to selected columns when they load
    LaunchedEffect(customFields) {
        if (customFields.isNotEmpty()) {
            selectedCustomFieldIds = customFields.map { it.id }
        }
    }
    
    val sortedLeads = remember(leads, sortOption, selectedStatus) {
        var filtered = if (selectedStatus != null) {
            leads.filter { it.status == selectedStatus }
        } else {
            leads
        }
        
        when (sortOption) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOption.DATE_ASC -> filtered.sortedBy { it.timestamp }
            SortOption.NAME_ASC -> filtered.sortedBy { it.name }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name }
            SortOption.STATUS -> filtered.sortedBy { it.status.ordinal }
        }
    }
    
    // Pagination calculations
    val totalPages = remember(sortedLeads) { 
        if (sortedLeads.isEmpty()) 1 else ((sortedLeads.size - 1) / PAGE_SIZE) + 1 
    }
    
    // Reset page when filter changes
    LaunchedEffect(selectedStatus, sortOption) {
        currentPage = 0
    }
    
    // Get current page data
    val paginatedLeads = remember(sortedLeads, currentPage) {
        val startIndex = currentPage * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, sortedLeads.size)
        if (startIndex < sortedLeads.size) {
            sortedLeads.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    // Bottom Sheet State
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    if (showCustomizeSheet) {
        ColumnCustomizationBottomSheet(
            sheetState = sheetState,
            selectedColumns = selectedColumns,
            onColumnsChanged = { selectedColumns = it },
            customFields = customFields,
            selectedCustomFieldIds = selectedCustomFieldIds,
            onCustomFieldsChanged = { selectedCustomFieldIds = it },
            onDismiss = { showCustomizeSheet = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lead Reports",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Export Button
                    var showExportMenu by remember { mutableStateOf(false) }
                    var isExporting by remember { mutableStateOf(false) }
                    
                    if (sortedLeads.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                enabled = !isExporting
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF10B981)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = "Export",
                                        tint = Color(0xFF10B981)
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                Text(
                                    "Export Filtered Leads",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.TableChart,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text("Export as CSV")
                                        }
                                    },
                                    onClick = {
                                        showExportMenu = false
                                        coroutineScope.launch {
                                            isExporting = true
                                            withContext(Dispatchers.IO) {
                                                val fileName = if (selectedStatus != null) {
                                                    LeadExportHelper.generateFileName("${selectedStatus!!.displayName}_Leads_Report")
                                                } else {
                                                    LeadExportHelper.generateFileName("All_Leads_Report")
                                                }
                                                val filePath = LeadExportHelper.exportToCSV(
                                                    context = context,
                                                    leads = sortedLeads,
                                                    fileName = fileName,
                                                    statusFilter = selectedStatus
                                                )
                                                
                                                withContext(Dispatchers.Main) {
                                                    isExporting = false
                                                    if (filePath != null) {
                                                        Toast.makeText(
                                                            context,
                                                            "CSV exported to $filePath",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Failed to export CSV",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                                
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = null,
                                                tint = Color(0xFF3B82F6),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text("Export as Excel")
                                        }
                                    },
                                    onClick = {
                                        showExportMenu = false
                                        coroutineScope.launch {
                                            isExporting = true
                                            withContext(Dispatchers.IO) {
                                                val fileName = if (selectedStatus != null) {
                                                    LeadExportHelper.generateFileName("${selectedStatus!!.displayName}_Leads_Report")
                                                } else {
                                                    LeadExportHelper.generateFileName("All_Leads_Report")
                                                }
                                                val filePath = LeadExportHelper.exportToExcel(
                                                    context = context,
                                                    leads = sortedLeads,
                                                    fileName = fileName,
                                                    statusFilter = selectedStatus
                                                )
                                                
                                                withContext(Dispatchers.Main) {
                                                    isExporting = false
                                                    if (filePath != null) {
                                                        Toast.makeText(
                                                            context,
                                                            "Excel exported to $filePath",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Failed to export Excel",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Customize Button
                    IconButton(
                        onClick = { showCustomizeSheet = true }
                    ) {
                        Icon(
                            Icons.Default.ViewColumn,
                            contentDescription = "Customize Columns",
                            tint = Color(0xFFEC4899)
                        )
                    }
                    
                    Box {
                        IconButton(onClick = { showSortMenu = !showSortMenu }) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = Color(0xFF8B5CF6)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOption == option) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Columns Info
        item {
            val totalColumns = selectedColumns.size + selectedCustomFieldIds.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$totalColumns columns selected" + if (selectedCustomFieldIds.isNotEmpty()) " (${selectedCustomFieldIds.size} custom)" else "",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                
                TextButton(onClick = { showCustomizeSheet = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFEC4899)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Customize",
                        fontSize = 12.sp,
                        color = Color(0xFFEC4899)
                    )
                }
            }
        }
        
        // Status Filter Chips
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { 
                            Text(
                                "All (${leads.size})",
                                fontWeight = if (selectedStatus == null) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            ) 
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            labelColor = Color(0xFF8B5CF6)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStatus == null,
                            borderColor = Color(0xFF8B5CF6),
                            selectedBorderColor = Color(0xFF8B5CF6),
                            borderWidth = 2.dp
                        )
                    )
                }
                items(LeadStatus.values().toList()) { status ->
                    val count = leads.count { it.status == status }
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = if (selectedStatus == status) null else status },
                        label = { 
                            Text(
                                "${status.displayName} ($count)",
                                fontWeight = if (selectedStatus == status) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            ) 
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(status.color),
                            selectedLabelColor = Color.White,
                            containerColor = Color(status.color).copy(alpha = 0.2f),
                            labelColor = Color(status.color)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStatus == status,
                            borderColor = Color(status.color),
                            selectedBorderColor = Color(status.color),
                            borderWidth = 2.dp
                        )
                    )
                }
            }
        }
        
        // Dynamic Table
        item {
            val scrollState = rememberScrollState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        selectedColumns.forEach { column ->
                            Row(
                                modifier = Modifier.width(column.width.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    column.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(column.category.color)
                                )
                                Text(
                                    column.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B5CF6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Custom Field Headers
                        customFields.filter { selectedCustomFieldIds.contains(it.id) }.forEach { field ->
                            Row(
                                modifier = Modifier.width(140.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    getIconForFieldType(field.fieldType),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = getIconTintForFieldType(field.fieldType)
                                )
                                Text(
                                    field.fieldName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEC4899),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Divider(color = Color(0xFF64748B).copy(alpha = 0.3f))

                    // Data Rows
                    if (paginatedLeads.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No leads found",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        paginatedLeads.forEach { lead ->
                            val isSelected = selectedLeadId == lead.id
                            val leadCustomValues = customFieldValuesCache[lead.id] ?: emptyMap()
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(scrollState)
                                    .background(
                                        if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                                    .clickable { 
                                        selectedLeadId = if (isSelected) null else lead.id 
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                selectedColumns.forEach { column ->
                                    ColumnValue(
                                        column = column,
                                        lead = lead,
                                        modifier = Modifier.width(column.width.dp)
                                    )
                                }
                                // Custom Field Values
                                customFields.filter { selectedCustomFieldIds.contains(it.id) }.forEach { field ->
                                    val value = leadCustomValues[field.id] ?: ""
                                    Text(
                                        value.ifEmpty { "-" },
                                        fontSize = 14.sp,
                                        color = if (value.isNotEmpty()) Color(0xFF94A3B8) else Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(140.dp)
                                    )
                                }
                            }
                            Divider(color = Color(0xFF64748B).copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }
        
        // Pagination Controls
        if (sortedLeads.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Info Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Showing ${(currentPage * PAGE_SIZE) + 1}-${minOf((currentPage + 1) * PAGE_SIZE, sortedLeads.size)} of ${sortedLeads.size}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                "Page ${currentPage + 1} of $totalPages",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Navigation Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // First Page
                            IconButton(
                                onClick = { currentPage = 0 },
                                enabled = currentPage > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FirstPage,
                                    contentDescription = "First",
                                    tint = if (currentPage > 0) Color(0xFF8B5CF6) else Color(0xFF64748B)
                                )
                            }
                            
                            // Previous Page
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "Previous",
                                    tint = if (currentPage > 0) Color(0xFF8B5CF6) else Color(0xFF64748B)
                                )
                            }
                            
                            // Page Numbers
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pageRange = remember(currentPage, totalPages) {
                                    val start = maxOf(0, currentPage - 2)
                                    val end = minOf(totalPages - 1, currentPage + 2)
                                    (start..end).toList()
                                }
                                
                                pageRange.forEach { page ->
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable { currentPage = page },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (page == currentPage) Color(0xFF8B5CF6) else Color.Transparent
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${page + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = if (page == currentPage) FontWeight.Bold else FontWeight.Normal,
                                                color = if (page == currentPage) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Next Page
                            IconButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                enabled = currentPage < totalPages - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Next",
                                    tint = if (currentPage < totalPages - 1) Color(0xFF8B5CF6) else Color(0xFF64748B)
                                )
                            }
                            
                            // Last Page
                            IconButton(
                                onClick = { currentPage = totalPages - 1 },
                                enabled = currentPage < totalPages - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.LastPage,
                                    contentDescription = "Last",
                                    tint = if (currentPage < totalPages - 1) Color(0xFF8B5CF6) else Color(0xFF64748B)
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
private fun ColumnValue(
    column: ReportColumn,
    lead: Lead,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateOnlyFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    when (column) {
        ReportColumn.LEAD_NAME -> {
            Text(
                lead.name,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.PHONE -> {
            Text(
                lead.phoneNumber,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.EMAIL -> {
            Text(
                lead.email.ifEmpty { "-" },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.STATUS -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(lead.status.color).copy(alpha = 0.2f),
                modifier = modifier
            ) {
                Text(
                    lead.status.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(lead.status.color),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
        ReportColumn.PRIORITY -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(lead.priority.color).copy(alpha = 0.2f),
                modifier = modifier
            ) {
                Text(
                    lead.priority.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(lead.priority.color),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
        ReportColumn.SOURCE -> {
            Text(
                lead.source,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.CATEGORY -> {
            Text(
                lead.category,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.CREATED_DATE -> {
            Text(
                dateOnlyFormat.format(Date(lead.timestamp)),
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                modifier = modifier
            )
        }
        ReportColumn.LAST_UPDATED -> {
            Text(
                dateFormat.format(Date(lead.timestamp)),
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                modifier = modifier
            )
        }
        ReportColumn.NEXT_FOLLOWUP -> {
            val nextFollowUp = lead.nextFollowUpDate
            Text(
                if (nextFollowUp != null && nextFollowUp > 0) {
                    dateOnlyFormat.format(Date(nextFollowUp))
                } else {
                    "Not scheduled"
                },
                fontSize = 14.sp,
                color = if (nextFollowUp != null && nextFollowUp < System.currentTimeMillis()) 
                    Color(0xFFEF4444) else Color(0xFF94A3B8),
                maxLines = 1,
                modifier = modifier
            )
        }
        ReportColumn.FOLLOWUP_STATUS -> {
            val status = if (lead.isFollowUpCompleted) "Completed" 
                        else if (lead.nextFollowUpDate != null) "Pending" 
                        else "None"
            val color = when (status) {
                "Completed" -> Color(0xFF10B981)
                "Pending" -> Color(0xFFF59E0B)
                else -> Color(0xFF64748B)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.2f),
                modifier = modifier
            ) {
                Text(
                    status,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        ReportColumn.FOLLOWUP_TYPE -> {
            val followUp = lead.followUps.firstOrNull { !it.isCompleted }
            Text(
                followUp?.type?.displayName ?: "-",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                modifier = modifier
            )
        }
        ReportColumn.FOLLOWUP_COUNT -> {
            Text(
                lead.followUps.size.toString(),
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                modifier = modifier
            )
        }
        ReportColumn.PRODUCT -> {
            Text(
                lead.product.ifEmpty { "-" },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.TAGS -> {
            if (lead.tags.isEmpty()) {
                Text("-", fontSize = 14.sp, color = Color(0xFF64748B), modifier = modifier)
            } else {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    lead.tags.take(2).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Text(
                                tag,
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                    if (lead.tags.size > 2) {
                        Text(
                            "+${lead.tags.size - 2}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
        ReportColumn.NOTES -> {
            Text(
                lead.notes.ifEmpty { "-" },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.LAST_MESSAGE -> {
            Text(
                lead.lastMessage.ifEmpty { "-" },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        ReportColumn.LEAD_SCORE -> {
            val scoreColor = when {
                lead.leadScore >= 70 -> Color(0xFF10B981)
                lead.leadScore >= 40 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = scoreColor.copy(alpha = 0.2f),
                modifier = modifier
            ) {
                Text(
                    "${lead.leadScore}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        ReportColumn.COUNTRY_CODE -> {
            Text(
                lead.countryCode.ifEmpty { "-" },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                modifier = modifier
            )
        }
        ReportColumn.ALTERNATE_PHONE -> {
            Text(
                lead.alternatePhone.ifEmpty { "-" },
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnCustomizationBottomSheet(
    sheetState: SheetState,
    selectedColumns: List<ReportColumn>,
    onColumnsChanged: (List<ReportColumn>) -> Unit,
    customFields: List<CustomField> = emptyList(),
    selectedCustomFieldIds: List<String> = emptyList(),
    onCustomFieldsChanged: (List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    var tempSelectedColumns by remember(selectedColumns) { mutableStateOf(selectedColumns.toMutableList()) }
    var tempSelectedCustomFieldIds by remember(selectedCustomFieldIds) { mutableStateOf(selectedCustomFieldIds.toMutableList()) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1a1a2e),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.size(40.dp, 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFF64748B)
                ) {}
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Column {
                Text(
                    "Customize Columns",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${tempSelectedColumns.size} columns selected",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        tempSelectedColumns = mutableListOf(
                            ReportColumn.LEAD_NAME,
                            ReportColumn.PHONE,
                            ReportColumn.SOURCE,
                            ReportColumn.STATUS,
                            ReportColumn.LAST_UPDATED
                        )
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF64748B)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64748B))
                ) {
                    Text("Reset", fontSize = 12.sp)
                }
                
                Button(
                    onClick = {
                        onColumnsChanged(tempSelectedColumns)
                        onCustomFieldsChanged(tempSelectedCustomFieldIds)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEC4899)
                    )
                ) {
                    Text("Apply", fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { 
                        tempSelectedColumns = ReportColumn.values().toMutableList()
                    },
                    label = { Text("Select All", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.SelectAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                        labelColor = Color(0xFF10B981),
                        leadingIconContentColor = Color(0xFF10B981)
                    )
                )
                
                AssistChip(
                    onClick = { 
                        tempSelectedColumns = mutableListOf(ReportColumn.LEAD_NAME)
                    },
                    label = { Text("Clear All", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                        labelColor = Color(0xFFEF4444),
                        leadingIconContentColor = Color(0xFFEF4444)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Columns by Category
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ColumnCategory.values().forEach { category ->
                    val columnsInCategory = ReportColumn.values().filter { it.category == category }
                    
                    item {
                        CategorySection(
                            category = category,
                            columns = columnsInCategory,
                            selectedColumns = tempSelectedColumns,
                            onColumnToggle = { column ->
                                tempSelectedColumns = if (tempSelectedColumns.contains(column)) {
                                    if (tempSelectedColumns.size > 1) {
                                        tempSelectedColumns.toMutableList().apply { remove(column) }
                                    } else {
                                        tempSelectedColumns // Keep at least one column
                                    }
                                } else {
                                    tempSelectedColumns.toMutableList().apply { add(column) }
                                }
                            }
                        )
                    }
                }
                
                // Custom Fields Section
                if (customFields.isNotEmpty()) {
                    item {
                        CustomFieldsColumnSection(
                            customFields = customFields,
                            selectedFieldIds = tempSelectedCustomFieldIds,
                            onFieldToggle = { fieldId ->
                                tempSelectedCustomFieldIds = if (tempSelectedCustomFieldIds.contains(fieldId)) {
                                    tempSelectedCustomFieldIds.toMutableList().apply { remove(fieldId) }
                                } else {
                                    tempSelectedCustomFieldIds.toMutableList().apply { add(fieldId) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun CategorySection(
    category: ColumnCategory,
    columns: List<ReportColumn>,
    selectedColumns: List<ReportColumn>,
    onColumnToggle: (ReportColumn) -> Unit
) {
    val categoryColor = Color(category.color)
    val selectedInCategory = columns.count { selectedColumns.contains(it) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252542)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Category Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.2f)
                    ) {
                        Box(modifier = Modifier.padding(6.dp)) {
                            Icon(
                                when (category) {
                                    ColumnCategory.BASIC -> Icons.Default.Info
                                    ColumnCategory.DATE -> Icons.Default.CalendarMonth
                                    ColumnCategory.FOLLOWUP -> Icons.Default.Schedule
                                    ColumnCategory.PRODUCT -> Icons.Default.Inventory
                                    ColumnCategory.ADDITIONAL -> Icons.Default.MoreHoriz
                                    ColumnCategory.CONTACT -> Icons.Default.ContactPhone
                                },
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Text(
                        category.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                }
                
                Text(
                    "$selectedInCategory/${columns.size}",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Column Chips
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                columns.forEach { column ->
                    val isSelected = selectedColumns.contains(column)
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { onColumnToggle(column) },
                        label = {
                            Text(
                                column.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                column.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor.copy(alpha = 0.3f),
                            selectedLabelColor = categoryColor,
                            selectedLeadingIconColor = categoryColor,
                            containerColor = Color(0xFF1a1a2e),
                            labelColor = Color(0xFF94A3B8),
                            iconColor = Color(0xFF64748B)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF64748B).copy(alpha = 0.3f),
                            selectedBorderColor = categoryColor,
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomFieldsColumnSection(
    customFields: List<CustomField>,
    selectedFieldIds: List<String>,
    onFieldToggle: (String) -> Unit
) {
    val categoryColor = Color(0xFFEC4899)
    val selectedCount = customFields.count { selectedFieldIds.contains(it.id) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252542)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Category Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.2f)
                    ) {
                        Box(modifier = Modifier.padding(6.dp)) {
                            Icon(
                                Icons.Default.DynamicForm,
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text("Custom Fields", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = categoryColor)
                }
                Text("$selectedCount/${customFields.size}", fontSize = 12.sp, color = Color(0xFF64748B))
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Custom Field Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                customFields.forEach { field ->
                    val isSelected = selectedFieldIds.contains(field.id)
                    val fieldColor = getIconTintForFieldType(field.fieldType)
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFieldToggle(field.id) },
                        label = {
                            Text(
                                field.fieldName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                getIconForFieldType(field.fieldType),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = fieldColor.copy(alpha = 0.3f),
                            selectedLabelColor = fieldColor,
                            selectedLeadingIconColor = fieldColor,
                            containerColor = Color(0xFF1a1a2e),
                            labelColor = Color(0xFF94A3B8),
                            iconColor = Color(0xFF64748B)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF64748B).copy(alpha = 0.3f),
                            selectedBorderColor = fieldColor,
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }
    }
}
