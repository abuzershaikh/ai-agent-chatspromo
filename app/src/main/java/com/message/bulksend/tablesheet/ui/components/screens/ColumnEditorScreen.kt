package com.message.bulksend.tablesheet.ui.components.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.*
import com.message.bulksend.tablesheet.ui.components.cells.parseSelectOptions

private val HeaderBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnEditorScreen(
    column: ColumnModel?,
    isNewColumn: Boolean = false,
    onSave: (name: String, type: String, width: Float, style: ColumnStyle, selectOptions: List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    // Load existing values from column
    val existingOptions = remember(column) {
        parseSelectOptions(column?.selectOptions)
    }
    
    var columnName by remember(column) { mutableStateOf(column?.name ?: "") }
    var selectedType by remember(column) { mutableStateOf(column?.type ?: FieldType.TEXT) }
    var columnWidth by remember(column) { mutableStateOf(column?.width ?: 1f) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var alignment by remember { mutableStateOf(0) }
    var selectOptions by remember(column) { mutableStateOf(existingOptions) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Tabs
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Properties", "Format", "Preview")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewColumn) "Add Column" else "Edit Column Properties", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (columnName.isNotBlank()) {
                            onSave(columnName.trim(), selectedType, columnWidth, 
                                ColumnStyle(isBold, isItalic, alignment), selectOptions)
                        }
                    }) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeaderBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .verticalScroll(rememberScrollState())
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = HeaderBlue
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Settings
                                    1 -> Icons.Default.FormatPaint
                                    else -> Icons.Default.Visibility
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }
            
            when (selectedTab) {
                0 -> PropertiesTab(
                    columnName = columnName,
                    onNameChange = { columnName = it },
                    selectedType = selectedType,
                    onTypeChange = { selectedType = it },
                    columnWidth = columnWidth,
                    onWidthChange = { columnWidth = it },
                    selectOptions = selectOptions,
                    onOptionsChange = { selectOptions = it }
                )
                1 -> StylesTab(
                    isBold = isBold,
                    onBoldChange = { isBold = it },
                    isItalic = isItalic,
                    onItalicChange = { isItalic = it },
                    alignment = alignment,
                    onAlignmentChange = { alignment = it }
                )
                2 -> PreviewTab(
                    columnName = columnName,
                    selectedType = selectedType,
                    isBold = isBold,
                    isItalic = isItalic
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Delete Button (only for existing columns)
            if (!isNewColumn) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Column?") },
            text = { Text("This will delete the column and all its data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PropertiesTab(
    columnName: String,
    onNameChange: (String) -> Unit,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    columnWidth: Float,
    onWidthChange: (Float) -> Unit,
    selectOptions: List<String>,
    onOptionsChange: (List<String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Column Name
        OutlinedTextField(
            value = columnName,
            onValueChange = onNameChange,
            label = { Text("Column Name") },
            placeholder = { Text("e.g., Name, Phone, Amount") },
            singleLine = true,
            trailingIcon = {
                if (columnName.isNotEmpty()) {
                    IconButton(onClick = { onNameChange("") }) {
                        Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(20.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HeaderBlue,
                focusedLabelColor = HeaderBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Column Type Section
        Text("Column Type", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
        Spacer(modifier = Modifier.height(12.dp))
        
        // Horizontal scrollable type selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(FieldTypes.allTypes) { typeConfig ->
                FieldTypeCard(
                    config = typeConfig,
                    isSelected = selectedType == typeConfig.type,
                    onClick = { onTypeChange(typeConfig.type) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Column Width
        Text("Column Width", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
        Spacer(modifier = Modifier.height(4.dp))
        Text("${String.format("%.2f", columnWidth)}", fontSize = 14.sp, color = Color.Gray)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (columnWidth > 0.5f) onWidthChange(columnWidth - 0.1f) }) {
                Icon(Icons.Default.Remove, "Decrease", tint = HeaderBlue)
            }
            Slider(
                value = columnWidth,
                onValueChange = onWidthChange,
                valueRange = 0.5f..6f,
                steps = 54,
                colors = SliderDefaults.colors(
                    thumbColor = HeaderBlue,
                    activeTrackColor = HeaderBlue
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { if (columnWidth < 6f) onWidthChange(columnWidth + 0.1f) }) {
                Icon(Icons.Default.Add, "Increase", tint = HeaderBlue)
            }
        }
        
        // Show options editor for SELECT types
        if (selectedType == FieldType.SELECT || selectedType == FieldType.MULTI_SELECT) {
            Spacer(modifier = Modifier.height(24.dp))
            DropdownOptionsEditor(
                options = selectOptions,
                onOptionsChange = onOptionsChange
            )
        }
        
        // Show currency selector for AMOUNT type
        if (selectedType == FieldType.AMOUNT) {
            Spacer(modifier = Modifier.height(24.dp))
            CurrencySelector(
                currentOptions = selectOptions.firstOrNull() ?: "",
                onCurrencyChange = { currencyOption ->
                    onOptionsChange(listOf(currencyOption))
                }
            )
        }
        
        // Show priority options editor for PRIORITY type
        if (selectedType == FieldType.PRIORITY) {
            Spacer(modifier = Modifier.height(24.dp))
            PriorityOptionsEditor(
                options = selectOptions,
                onOptionsChange = onOptionsChange
            )
        }
    }
}

@Composable
private fun FieldTypeCard(
    config: FieldTypeConfig,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) config.color.copy(alpha = 0.15f) else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) config.color else Color(0xFFE5E7EB)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSelected) config.color else config.color.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    config.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else config.color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                config.name,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) config.color else Color(0xFF6B7280),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DropdownOptionsEditor(
    options: List<String>,
    onOptionsChange: (List<String>) -> Unit
) {
    var newOption by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Dropdown Options", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add new option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newOption,
                    onValueChange = { newOption = it },
                    placeholder = { Text("Add option...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HeaderBlue
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newOption.isNotBlank()) {
                            onOptionsChange(options + newOption.trim())
                            newOption = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, "Add", tint = HeaderBlue)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Options list
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { onOptionsChange(options.filterIndexed { i, _ -> i != index }) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            if (options.isEmpty()) {
                Text(
                    "No options added yet",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StylesTab(
    isBold: Boolean,
    onBoldChange: (Boolean) -> Unit,
    isItalic: Boolean,
    onItalicChange: (Boolean) -> Unit,
    alignment: Int,
    onAlignmentChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Styles", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Bold
                StyleButton(
                    icon = Icons.Default.FormatBold,
                    label = "Bold",
                    isSelected = isBold,
                    onClick = { onBoldChange(!isBold) }
                )
                // Italic
                StyleButton(
                    icon = Icons.Default.FormatItalic,
                    label = "Italic",
                    isSelected = isItalic,
                    onClick = { onItalicChange(!isItalic) }
                )
                // Align Left
                StyleButton(
                    icon = Icons.Default.FormatAlignLeft,
                    label = "Left",
                    isSelected = alignment == 0,
                    onClick = { onAlignmentChange(0) }
                )
                // Align Center
                StyleButton(
                    icon = Icons.Default.FormatAlignCenter,
                    label = "Center",
                    isSelected = alignment == 1,
                    onClick = { onAlignmentChange(1) }
                )
                // Align Right
                StyleButton(
                    icon = Icons.Default.FormatAlignRight,
                    label = "Right",
                    isSelected = alignment == 2,
                    onClick = { onAlignmentChange(2) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sample Preview
        Text("Sample Text", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Text(
                "Sample Text",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                textAlign = when (alignment) {
                    0 -> TextAlign.Start
                    1 -> TextAlign.Center
                    else -> TextAlign.End
                },
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun StyleButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) HeaderBlue.copy(alpha = 0.1f) else Color.Transparent)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) HeaderBlue else Color(0xFF6B7280),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (isSelected) HeaderBlue else Color(0xFF6B7280)
        )
    }
}

@Composable
private fun PreviewTab(
    columnName: String,
    selectedType: String,
    isBold: Boolean,
    isItalic: Boolean
) {
    val typeConfig = FieldTypes.getConfig(selectedType)
    
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Preview", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
        Spacer(modifier = Modifier.height(16.dp))
        
        // Column Header Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = HeaderBlue),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(typeConfig.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    columnName.ifEmpty { "Column Name" },
                    color = Color.White,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
        
        // Sample Cell Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                when (selectedType) {
                    FieldType.CHECKBOX -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = true, onCheckedChange = null)
                            Text("Checked", color = Color.Gray)
                        }
                    }
                    FieldType.SELECT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Select option...", color = Color.Gray)
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
                        }
                    }
                    FieldType.MULTI_SELECT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Paid") },
                                leadingIcon = { Icon(Icons.Default.Check, null) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("Priority") },
                                leadingIcon = { Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                    FieldType.DATE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("13/12/2025", color = Color(0xFF374151))
                        }
                    }
                    FieldType.DATETIME -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("13/12/2025 10:30", color = Color(0xFF374151))
                        }
                    }
                    FieldType.TIME -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("10:30 AM", color = Color(0xFF374151))
                        }
                    }
                    FieldType.PHONE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+91 98765 43210", color = Color(0xFF374151))
                        }
                    }
                    FieldType.EMAIL -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("example@email.com", color = Color(0xFF374151))
                        }
                    }
                    FieldType.URL -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("https://example.com", color = Color(0xFF1D4ED8))
                        }
                    }
                    FieldType.MAP -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("28.6139, 77.2090", color = Color(0xFF374151))
                        }
                    }
                    FieldType.IMAGE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, null, tint = typeConfig.color)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tap to add image", color = Color.Gray)
                        }
                    }
                    FieldType.AMOUNT -> {
                        Text("₹ 1,500.00", color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                    }
                    FieldType.NUMBER -> {
                        Text("12,345", color = Color(0xFF374151))
                    }
                    FieldType.DECIMAL -> {
                        Text("1234.56", color = Color(0xFF374151))
                    }
                    FieldType.MULTILINE -> {
                        Text("Line 1\nLine 2", color = Color(0xFF374151))
                    }
                    FieldType.JSON -> {
                        Text("{\"status\":\"ok\"}", color = Color(0xFF374151))
                    }
                    FieldType.FILE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, tint = typeConfig.color, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("/storage/docs/invoice.pdf", color = Color(0xFF374151))
                        }
                    }
                    FieldType.FORMULA -> {
                        Text("=CONCAT(A1,\" \",B1)", color = Color(0xFF0EA5E9), fontWeight = FontWeight.Medium)
                    }
                    FieldType.PRIORITY -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("High", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                        }
                    }
                    else -> {
                        Text("Sample text value", color = Color(0xFF374151))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Type Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = typeConfig.color.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(typeConfig.icon, null, tint = typeConfig.color, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(typeConfig.name, fontWeight = FontWeight.SemiBold, color = typeConfig.color)
                    Text(typeConfig.description, fontSize = 13.sp, color = Color(0xFF6B7280))
                }
            }
        }
    }
}

@Composable
private fun CurrencySelector(
    currentOptions: String,
    onCurrencyChange: (String) -> Unit
) {
    val context = LocalContext.current
    val currencies = remember { CurrencyHelper.loadCurrencies(context) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Parse current options
    val parsed = CurrencyHelper.parseCurrencyOptions(currentOptions)
    var selectedCode by remember(currentOptions) { mutableStateOf(parsed?.first ?: "INR") }
    var selectedSymbol by remember(currentOptions) { mutableStateOf(parsed?.second ?: "₹") }
    var symbolPosition by remember(currentOptions) { mutableStateOf(parsed?.third ?: "left") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Currency Settings", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Currency selector button
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { showCurrencyPicker = true },
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedSymbol, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HeaderBlue)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(selectedCode, fontWeight = FontWeight.Medium)
                            Text(
                                currencies.find { it.code == selectedCode }?.country ?: "",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Symbol position selector
            Text("Symbol Position", fontSize = 14.sp, color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left position
                OutlinedCard(
                    modifier = Modifier.weight(1f).clickable {
                        symbolPosition = "left"
                        onCurrencyChange(CurrencyHelper.formatCurrencyOptions(selectedCode, selectedSymbol, "left"))
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        if (symbolPosition == "left") 2.dp else 1.dp,
                        if (symbolPosition == "left") HeaderBlue else Color(0xFFE5E7EB)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$selectedSymbol 100", fontWeight = FontWeight.Medium)
                        Text("Left", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                
                // Right position
                OutlinedCard(
                    modifier = Modifier.weight(1f).clickable {
                        symbolPosition = "right"
                        onCurrencyChange(CurrencyHelper.formatCurrencyOptions(selectedCode, selectedSymbol, "right"))
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        if (symbolPosition == "right") 2.dp else 1.dp,
                        if (symbolPosition == "right") HeaderBlue else Color(0xFFE5E7EB)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("100 $selectedSymbol", fontWeight = FontWeight.Medium)
                        Text("Right", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
    
    // Currency picker dialog
    if (showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            title = { Text("Select Currency") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search currency...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HeaderBlue)
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Currency list
                    val filtered = currencies.filter {
                        searchQuery.isEmpty() ||
                        it.code.contains(searchQuery, ignoreCase = true) ||
                        it.country.contains(searchQuery, ignoreCase = true)
                    }
                    
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(filtered) { currency ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCode = currency.code
                                        selectedSymbol = currency.symbol
                                        onCurrencyChange(CurrencyHelper.formatCurrencyOptions(currency.code, currency.symbol, symbolPosition))
                                        showCurrencyPicker = false
                                        searchQuery = ""
                                    }
                                    .background(
                                        if (selectedCode == currency.code) HeaderBlue.copy(alpha = 0.1f) else Color.Transparent
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    currency.symbol,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeaderBlue,
                                    modifier = Modifier.width(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(currency.code, fontWeight = FontWeight.Medium)
                                    Text(currency.country, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (selectedCode == currency.code) {
                                    Icon(Icons.Default.Check, null, tint = HeaderBlue)
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF3F4F6))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyPicker = false; searchQuery = "" }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PriorityOptionsEditor(
    options: List<String>,
    onOptionsChange: (List<String>) -> Unit
) {
    // Parse existing priority options from string format
    val priorityOptions = remember(options) {
        if (options.isNotEmpty() && options.first().contains(":")) {
            PriorityOption.parseFromString(options.first())
        } else {
            PriorityOption.DEFAULT_OPTIONS
        }
    }
    
    var currentOptions by remember(options) { mutableStateOf(priorityOptions) }
    var newOptionName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PriorityOption.AVAILABLE_COLORS.first()) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    // Update parent when options change
    LaunchedEffect(currentOptions) {
        onOptionsChange(listOf(PriorityOption.toString(currentOptions)))
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Priority Options", fontWeight = FontWeight.Medium, color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Add new priority option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newOptionName,
                    onValueChange = { newOptionName = it },
                    placeholder = { Text("Priority name...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HeaderBlue
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                // Color picker button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(selectedColor, CircleShape)
                        .border(2.dp, Color(0xFFE5E7EB), CircleShape)
                        .clickable { showColorPicker = true }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (newOptionName.isNotBlank()) {
                            currentOptions = currentOptions + PriorityOption(newOptionName.trim(), selectedColor)
                            newOptionName = ""
                            // Cycle to next color
                            val currentIndex = PriorityOption.AVAILABLE_COLORS.indexOf(selectedColor)
                            selectedColor = PriorityOption.AVAILABLE_COLORS[(currentIndex + 1) % PriorityOption.AVAILABLE_COLORS.size]
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, "Add", tint = HeaderBlue)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Priority options list
            currentOptions.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(option.color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color indicator
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(option.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        option.name,
                        modifier = Modifier.weight(1f),
                        color = option.color,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { 
                            currentOptions = currentOptions.filterIndexed { i, _ -> i != index }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            if (currentOptions.isEmpty()) {
                Text(
                    "No priority options added yet",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
    
    // Color picker dialog
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Select Color") },
            text = {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PriorityOption.AVAILABLE_COLORS) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .border(
                                    if (selectedColor == color) 3.dp else 1.dp,
                                    if (selectedColor == color) Color(0xFF333333) else Color(0xFFE5E7EB),
                                    CircleShape
                                )
                                .clickable {
                                    selectedColor = color
                                    showColorPicker = false
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}
