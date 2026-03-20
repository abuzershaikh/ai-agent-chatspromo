package com.message.bulksend.autorespond.tools.sheetconnect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Data ──────────────────────────────────────────────────────────────────────

enum class SheetActionType {
    CREATE_SPREADSHEET,
    CREATE_SHEET_TAB,
    APPEND_ROW,
    GET_ROWS,
    UPDATE_ROW,
    CLEAR_SHEET,
    SAVE_MAPPING,
    MY_SHEETS
}

data class SheetAction(
    val id: SheetActionType,
    val label: String,
    val category: String
)

val SHEET_ACTIONS = listOf(
    SheetAction(SheetActionType.MY_SHEETS,          "My Spreadsheets",     "My Spreadsheets"),
    SheetAction(SheetActionType.CREATE_SPREADSHEET, "Create spreadsheet",  "Document Actions"),
    SheetAction(SheetActionType.CREATE_SHEET_TAB,  "Create sheet tab",    "Sheet Actions"),
    SheetAction(SheetActionType.APPEND_ROW,         "Append row",         "Sheet Actions"),
    SheetAction(SheetActionType.GET_ROWS,           "Get row(s)",         "Sheet Actions"),
    SheetAction(SheetActionType.UPDATE_ROW,         "Update row",         "Sheet Actions"),
    SheetAction(SheetActionType.CLEAR_SHEET,        "Clear sheet",        "Sheet Actions"),
    SheetAction(SheetActionType.SAVE_MAPPING,       "Save column mapping","Sheet Actions")
)

// ─── Activity ──────────────────────────────────────────────────────────────────

class SheetMappingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SheetActionScreen(onBackClicked = { finish() }) }
    }
}

// ─── Root screen ───────────────────────────────────────────────────────────────

private val BG        = Color(0xFF111111)
private val SIDEBAR   = Color(0xFF1C1C1C)
private val ROW_SEL   = Color(0xFF213A2C)
private val GREEN     = Color(0xFF34A853)
private val DIVIDER   = Color(0xFF2D2D2D)
private val TEXT_DIM  = Color(0xFF888888)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetActionScreen(onBackClicked: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { SheetConnectManager(context) }
    val scope   = rememberCoroutineScope()

    var sidebarOpen    by remember { mutableStateOf(true) }
    var searchQuery    by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf<SheetAction?>(null) }
    var createdSheets  by remember { mutableStateOf<List<CreatedSheet>>(emptyList()) }

    // Load created sheets on startup
    LaunchedEffect(Unit) {
        createdSheets = manager.getCreatedSheets()
    }

    // Callback used by the Create panel to refresh sidebar after creation
    val onSheetCreated: (CreatedSheet) -> Unit = { sheet ->
        createdSheets = listOf(sheet) + createdSheets.filter { it.spreadsheetId != sheet.spreadsheetId }
    }

    val categories = remember(searchQuery) {
        SHEET_ACTIONS
            .filter { it.label.contains(searchQuery, ignoreCase = true) }
            .groupBy { it.category }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
    ) {
        // ── Sidebar ────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = sidebarOpen,
            enter   = slideInHorizontally { -it } + fadeIn(),
            exit    = slideOutHorizontally { -it } + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .background(SIDEBAR)
            ) {
                // Sidebar top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { sidebarOpen = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = "Close menu", tint = Color.White)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Actions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search…", color = TEXT_DIM, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TEXT_DIM, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(48.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = Color(0xFF252525),
                        unfocusedContainerColor = Color(0xFF252525),
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White,
                        focusedBorderColor      = GREEN,
                        unfocusedBorderColor    = DIVIDER
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(Modifier.height(8.dp))

                // Category list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    categories.forEach { (cat, actions) ->
                        item {
                            Text(
                                cat.uppercase(),
                                color = TEXT_DIM,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(actions, key = { it.id }) { action ->
                            val isSelected = selectedAction?.id == action.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) ROW_SEL else Color.Transparent)
                                    .clickable {
                                        selectedAction = action
                                        sidebarOpen = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GREEN),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⊞", color = Color.White, fontSize = 10.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    action.label,
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected) {
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(GREEN)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Thin divider between sidebar and content
        if (sidebarOpen) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(DIVIDER))
        }

        // ── Main content area ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BG)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!sidebarOpen) {
                    IconButton(onClick = { sidebarOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = Color.White)
                    }
                }
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    selectedAction?.label ?: "Google Sheets",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            HorizontalDivider(color = DIVIDER, thickness = 1.dp)

            // Configuration panel
            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedAction == null) {
                    // Empty state
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2F7B50)),
                            contentAlignment = Alignment.Center
                        ) { Text("⊞", color = Color.White, fontSize = 30.sp) }
                        Spacer(Modifier.height(16.dp))
                        Text("Select an action from the menu", color = TEXT_DIM, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("← tap any item on the left", color = Color(0xFF555555), fontSize = 12.sp)
                    }
                } else {
                    // Show config for the selected action
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ActionConfigContent(
                            action        = selectedAction!!,
                            manager       = manager,
                            context       = context,
                            createdSheets = createdSheets,
                            onSheetCreated = onSheetCreated
                        )
                    }
                }

                if (sidebarOpen) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                sidebarOpen = false
                            }
                    )
                }
            }
        }
    }
}

// ─── Action config panel content ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigContent(
    action: SheetAction,
    manager: SheetConnectManager,
    context: android.content.Context,
    createdSheets: List<CreatedSheet> = emptyList(),
    onSheetCreated: (CreatedSheet) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // Section header
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(GREEN),
            contentAlignment = Alignment.Center
        ) { Text("⊞", color = Color.White, fontSize = 18.sp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(action.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(action.category, color = TEXT_DIM, fontSize = 12.sp)
        }
    }

    HorizontalDivider(color = DIVIDER)

    when (action.id) {

        // ── My Spreadsheets ────────────────────────────────────────────────────
        SheetActionType.MY_SHEETS -> {
            if (createdSheets.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No spreadsheets created yet", color = TEXT_DIM, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Use \"Create spreadsheet\" to make one", color = Color(0xFF555555), fontSize = 12.sp)
                }
            } else {
                createdSheets.forEach { sheet ->
                    CreatedSheetCard(sheet = sheet, context = context)
                }
            }
        }


        SheetActionType.CREATE_SPREADSHEET -> {
            var title   by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var result  by remember { mutableStateOf("") }

            // Each field: Pair(fieldName, fieldType)
            val fields = remember { mutableStateListOf(Pair("", "Text")) }

            val fieldTypes = listOf("Text", "Number", "Date", "Email", "Phone", "URL", "Checkbox", "Currency")

            ConfigLabel("Spreadsheet Title")
            DarkField(title, { title = it }, "e.g. My CRM Data")

            Spacer(Modifier.height(4.dp))

            // Fields header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Column Fields",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                // + Add field button
                OutlinedButton(
                    onClick = { fields.add(Pair("", "Text")) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GREEN),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GREEN),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add field", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Field", fontSize = 12.sp)
                }
            }

            // Field rows
            fields.forEachIndexed { index, (fieldName, fieldType) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Field number badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = TEXT_DIM, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Field name input
                    OutlinedTextField(
                        value = fieldName,
                        onValueChange = { newName -> fields[index] = Pair(newName, fieldType) },
                        placeholder = { Text("Field name", color = Color(0xFF555555), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = Color(0xFF222222),
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color(0xFFDDDDDD),
                            focusedBorderColor      = GREEN,
                            unfocusedBorderColor    = Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )

                    // Field type dropdown (compact)
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier.width(110.dp)
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = fieldType,
                            onValueChange = { },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor   = Color(0xFF222222),
                                unfocusedContainerColor = Color(0xFF1E1E1E),
                                focusedTextColor        = Color(0xFF4CAF50),
                                unfocusedTextColor      = Color(0xFF4CAF50),
                                focusedBorderColor      = GREEN,
                                unfocusedBorderColor    = Color(0xFF333333)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E))
                        ) {
                            fieldTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        fields[index] = Pair(fieldName, type)
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Remove button (don't show if only one field)
                    if (fields.size > 1) {
                        IconButton(
                            onClick = { fields.removeAt(index) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove field",
                                tint = Color(0xFFFF5555),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (result.isNotBlank()) {
                ResultBox(result)
            }

            ConfigButton(
                label   = "Create Spreadsheet",
                loading = loading,
                enabled = title.isNotBlank() && fields.any { it.first.isNotBlank() }
            ) {
                loading = true
                scope.launch {
                    manager.createSheet(title.trim()).onSuccess { (id, url) ->
                        // Write field names as header row
                        val headerRow = listOf(fields.map { it.first })
                        manager.writeSheetData(id, "Sheet1!A1", headerRow)
                        result = "✅ Created!\nID: $id\nURL: ${url.ifBlank { "—" }}"
                        // Auto-save to My Spreadsheets
                        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                        val saved = CreatedSheet(title = title.trim(), spreadsheetId = id, spreadsheetUrl = url, createdAt = dateStr)
                        manager.saveCreatedSheet(saved)
                        onSheetCreated(saved)
                        Toast.makeText(context, "Spreadsheet created with ${fields.size} fields!", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                    loading = false
                }
            }
        }

        // ── Create Sheet Tab ───────────────────────────────────────────────────
        SheetActionType.CREATE_SHEET_TAB -> {
            var url     by remember { mutableStateOf("") }
            var tabName by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }

            ConfigLabel("Spreadsheet URL or ID")
            DarkField(url, { url = it }, "Paste your Google Sheet link")
            ConfigLabel("New Tab Name")
            DarkField(tabName, { tabName = it }, "e.g. Orders_March")

            ConfigButton("Create Sheet Tab", loading, url.isNotBlank() && tabName.isNotBlank()) {
                loading = true
                scope.launch {
                    Toast.makeText(context, "Tab creation via batchUpdate — coming soon!", Toast.LENGTH_LONG).show()
                    loading = false
                }
            }
        }

        // ── Append Row ─────────────────────────────────────────────────────────
        SheetActionType.APPEND_ROW -> {
            var spreadsheetUrl by remember { mutableStateOf("") }
            var sheetsList     by remember { mutableStateOf<List<SheetInfo>>(emptyList()) }
            var selectedSheet  by remember { mutableStateOf("") }
            var columns        by remember { mutableStateOf<List<String>>(emptyList()) }
            var isFetching     by remember { mutableStateOf(false) }
            var loading        by remember { mutableStateOf(false) }
            var nameCol        by remember { mutableStateOf("") }
            var phoneCol       by remember { mutableStateOf("") }
            var emailCol       by remember { mutableStateOf("") }
            var notesCol       by remember { mutableStateOf("") }

            ConfigLabel("Spreadsheet URL or ID")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkField(spreadsheetUrl, { spreadsheetUrl = it }, "Paste your Google Sheet link", Modifier.weight(1f))
                Button(
                    onClick = {
                        isFetching = true
                        scope.launch {
                            manager.fetchSheetMetadata(spreadsheetUrl.trim()).onSuccess { meta ->
                                sheetsList = meta.sheets ?: emptyList()
                                Toast.makeText(context, "Loaded ${sheetsList.size} sheets", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            isFetching = false
                        }
                    },
                    enabled = !isFetching && spreadsheetUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isFetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Fetch", color = Color.White)
                }
            }

            if (sheetsList.isNotEmpty()) {
                ConfigLabel("Select Sheet Tab")
                DarkDropdownMenu(sheetsList.map { it.sheetName }, selectedSheet, "Choose sheet") {
                    selectedSheet = it
                    columns = sheetsList.find { s -> s.sheetName == it }?.columns ?: emptyList()
                    nameCol = ""; phoneCol = ""; emailCol = ""; notesCol = ""
                }
            }

            if (columns.isNotEmpty()) {
                ConfigLabel("Map App Fields → Sheet Columns")
                DarkDropdownMenu(columns, nameCol, "Name field", noMap = true)   { nameCol = it }
                DarkDropdownMenu(columns, phoneCol, "Phone field", noMap = true) { phoneCol = it }
                DarkDropdownMenu(columns, emailCol, "Email field", noMap = true) { emailCol = it }
                DarkDropdownMenu(columns, notesCol, "Notes field", noMap = true) { notesCol = it }

                ConfigButton("Save Mapping", loading, selectedSheet.isNotBlank()) {
                    loading = true
                    scope.launch {
                        manager.saveMappingConfig(SheetMappingConfig(
                            spreadsheetUrlId = spreadsheetUrl.trim(),
                            spreadsheetId    = spreadsheetUrl.trim(),
                            sheetName        = selectedSheet,
                            nameColumn       = nameCol,
                            phoneColumn      = phoneCol,
                            emailColumn      = emailCol,
                            notesColumn      = notesCol
                        )).onSuccess {
                            Toast.makeText(context, "Mapping saved!", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                        loading = false
                    }
                }
            }
        }

        // ── Get Rows ───────────────────────────────────────────────────────────
        SheetActionType.GET_ROWS -> {
            var url     by remember { mutableStateOf("") }
            var range   by remember { mutableStateOf("Sheet1!A:Z") }
            var loading by remember { mutableStateOf(false) }
            var result  by remember { mutableStateOf("") }

            ConfigLabel("Spreadsheet URL or ID")
            DarkField(url, { url = it }, "Paste your Google Sheet link")
            ConfigLabel("Range")
            DarkField(range, { range = it }, "e.g. Sheet1!A:Z")

            if (result.isNotBlank()) ResultBox(result)

            ConfigButton("Get Rows", loading, url.isNotBlank()) {
                loading = true
                scope.launch {
                    manager.readSheetData(url.trim(), range.trim()).onSuccess { rows ->
                        result = if (rows.isEmpty()) "(No data)" else
                            rows.take(10).mapIndexed { i, row -> "Row ${i+1}: ${row.joinToString(", ")}" }.joinToString("\n")
                        Toast.makeText(context, "Read ${rows.size} rows", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                    loading = false
                }
            }
        }

        // ── Update Row ─────────────────────────────────────────────────────────
        SheetActionType.UPDATE_ROW -> {
            var url     by remember { mutableStateOf("") }
            var range   by remember { mutableStateOf("Sheet1!A2:D2") }
            var values  by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }

            ConfigLabel("Spreadsheet URL or ID")
            DarkField(url, { url = it }, "Paste your Google Sheet link")
            ConfigLabel("Range to Update")
            DarkField(range, { range = it }, "e.g. Sheet1!A2:D2")
            ConfigLabel("Values (comma-separated)")
            DarkField(values, { values = it }, "e.g. John, +919999, john@mail.com")

            ConfigButton("Update Row", loading, url.isNotBlank() && values.isNotBlank()) {
                loading = true
                scope.launch {
                    val rowData = listOf(values.split(",").map { it.trim() })
                    manager.updateSheetData(url.trim(), range.trim(), rowData)
                        .onSuccess { Toast.makeText(context, "Row updated!", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
                    loading = false
                }
            }
        }

        // ── Clear Sheet ────────────────────────────────────────────────────────
        SheetActionType.CLEAR_SHEET -> {
            var url     by remember { mutableStateOf("") }
            var sheet   by remember { mutableStateOf("Sheet1") }
            var loading by remember { mutableStateOf(false) }

            ConfigLabel("Spreadsheet URL or ID")
            DarkField(url, { url = it }, "Paste your Google Sheet link")
            ConfigLabel("Sheet Tab Name")
            DarkField(sheet, { sheet = it }, "e.g. Sheet1")

            ConfigButton("Clear Sheet", loading, url.isNotBlank()) {
                loading = true
                scope.launch {
                    manager.updateSheetData(url.trim(), "$sheet!A:Z", listOf(listOf("")))
                        .onSuccess { Toast.makeText(context, "Sheet cleared!", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
                    loading = false
                }
            }
        }

        // ── Save Mapping (n8n-style: per-column field assignment) ──────────────
        SheetActionType.SAVE_MAPPING -> {
            var url           by remember { mutableStateOf("") }
            var sheetsList    by remember { mutableStateOf<List<SheetInfo>>(emptyList()) }
            var selectedSheet by remember { mutableStateOf("") }
            var columns       by remember { mutableStateOf<List<String>>(emptyList()) }
            var isFetching    by remember { mutableStateOf(false) }
            var loading       by remember { mutableStateOf(false) }

            // n8n-style: per column -> which app field fills it
            // Map<ColumnName, AppField>. AppField can be one of APP_FIELDS or blank (skip)
            val columnMapping = remember { mutableStateMapOf<String, String>() }

            val APP_FIELDS = listOf(
                "— Skip —",
                "Contact Name",
                "Phone Number",
                "Email Address",
                "Notes / Message",
                "Timestamp (auto)"
            )

            // Prefill existing config
            LaunchedEffect(Unit) {
                manager.getMappingConfig()?.let { config ->
                    url           = config.spreadsheetUrlId
                    selectedSheet = config.sheetName
                    // Rebuild reverse mapping from saved columns
                    if (config.nameColumn.isNotBlank())  columnMapping[config.nameColumn]  = "Contact Name"
                    if (config.phoneColumn.isNotBlank()) columnMapping[config.phoneColumn] = "Phone Number"
                    if (config.emailColumn.isNotBlank()) columnMapping[config.emailColumn] = "Email Address"
                    if (config.notesColumn.isNotBlank()) columnMapping[config.notesColumn] = "Notes / Message"
                    // Load columns from sheet
                    if (url.isNotBlank()) {
                        manager.fetchSheetMetadata(url).onSuccess { meta ->
                            sheetsList = meta.sheets ?: emptyList()
                            columns = sheetsList.find { it.sheetName == selectedSheet }?.columns ?: emptyList()
                        }
                    }
                }
            }

            // ── URL + Fetch row ────────────────────────────────────────────────
            ConfigLabel("Spreadsheet URL or ID")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkField(url, { url = it }, "Paste Google Sheet link or ID", Modifier.weight(1f))
                Button(
                    onClick = {
                        isFetching = true
                        scope.launch {
                            manager.fetchSheetMetadata(url.trim()).onSuccess { meta ->
                                sheetsList = meta.sheets ?: emptyList()
                                Toast.makeText(context, "Loaded ${sheetsList.size} sheet(s)", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Fetch error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            isFetching = false
                        }
                    },
                    enabled = !isFetching && url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isFetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Fetch", color = Color.White)
                }
            }

            // ── Sheet Tab selector ────────────────────────────────────────────
            if (sheetsList.isNotEmpty()) {
                ConfigLabel("Sheet Tab")
                DarkDropdownMenu(sheetsList.map { it.sheetName }, selectedSheet, "Choose sheet tab") { choice ->
                    selectedSheet = choice
                    columns = sheetsList.find { it.sheetName == choice }?.columns ?: emptyList()
                    columnMapping.clear()
                }
            }

            // ── n8n-style column mapping ──────────────────────────────────────
            if (columns.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Color(0xFF2A3A2A))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sheet Column", color = TEXT_DIM, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("App Field", color = TEXT_DIM, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }

                // One row per column
                columns.forEachIndexed { index, colName ->
                    val assigned = columnMapping[colName] ?: "— Skip —"
                    val isAssigned = assigned != "— Skip —"
                    val isLast = index == columns.lastIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(if (isLast) RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(0.dp))
                            .background(if (isAssigned) Color(0xFF1C2C1C) else Color(0xFF1A1A1A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left: column name pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF222222))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Column letter badge
                                val letter = ('A' + index).toString()
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isAssigned) GREEN else Color(0xFF444444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(letter, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    colName,
                                    color = if (isAssigned) Color.White else Color(0xFFAAAAAA),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Arrow
                        Text("→", color = if (isAssigned) GREEN else Color(0xFF555555), fontSize = 16.sp)

                        // Right: app field dropdown
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAssigned) Color(0xFF1F3A2F) else Color(0xFF222222))
                                    .clickable { expanded = true }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        assigned,
                                        color = if (isAssigned) GREEN else Color(0xFF666666),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                                }
                            }
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color(0xFF1E1E1E))
                            ) {
                                APP_FIELDS.forEach { field ->
                                    val isCurrent = assigned == field
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                field,
                                                color = if (field == "— Skip —") TEXT_DIM else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            if (field == "— Skip —") {
                                                columnMapping.remove(colName)
                                            } else {
                                                // Remove previous assignment of this app field (avoid duplicates)
                                                columnMapping.entries.removeIf { it.value == field }
                                                columnMapping[colName] = field
                                            }
                                            expanded = false
                                        },
                                        trailingIcon = if (isCurrent && field != "— Skip —") {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = GREEN, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    if (!isLast) {
                        HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
                    }
                }

                // Summary chips
                val assignedCount = columnMapping.size
                if (assignedCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$assignedCount column${if (assignedCount > 1) "s" else ""} mapped",
                        color = GREEN,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Save Button
                ConfigButton("Save Column Mapping", loading, selectedSheet.isNotBlank() && columnMapping.isNotEmpty()) {
                    loading = true
                    scope.launch {
                        // Convert reverse map back to SheetMappingConfig fields
                        fun colFor(fieldName: String) = columnMapping.entries.find { it.value == fieldName }?.key ?: ""

                        manager.saveMappingConfig(SheetMappingConfig(
                            spreadsheetUrlId = url.trim(),
                            spreadsheetId    = url.trim(),
                            sheetName        = selectedSheet,
                            nameColumn       = colFor("Contact Name"),
                            phoneColumn      = colFor("Phone Number"),
                            emailColumn      = colFor("Email Address"),
                            notesColumn      = colFor("Notes / Message")
                        )).onSuccess {
                            Toast.makeText(context, "Mapping saved! ${columnMapping.size} columns configured.", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                        loading = false
                    }
                }
            }
        }
    }
}

// ─── Reusable building blocks ───────────────────────────────────────────────────

@Composable
fun ConfigLabel(text: String) {
    Text(text, color = TEXT_DIM, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder  = { Text(placeholder, color = Color(0xFF555555), fontSize = 13.sp) },
        modifier     = modifier,
        singleLine   = true,
        colors       = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = Color(0xFF1E1E1E),
            unfocusedContainerColor = Color(0xFF1A1A1A),
            focusedTextColor        = Color.White,
            unfocusedTextColor      = Color(0xFFDDDDDD),
            focusedBorderColor      = GREEN,
            unfocusedBorderColor    = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkDropdownMenu(
    options: List<String>,
    selected: String,
    label: String,
    noMap: Boolean = false,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value    = selected.ifBlank { if (noMap) "— Not Mapped —" else label },
            onValueChange = { },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors   = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = Color(0xFF1E1E1E),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                focusedTextColor        = Color.White,
                unfocusedTextColor      = Color(0xFFDDDDDD),
                focusedBorderColor      = GREEN,
                unfocusedBorderColor    = Color(0xFF333333)
            ),
            shape = RoundedCornerShape(8.dp)
        )
        ExposedDropdownMenu(
            expanded          = expanded,
            onDismissRequest  = { expanded = false },
            modifier          = Modifier.background(Color(0xFF1E1E1E))
        ) {
            if (noMap) DropdownMenuItem(
                text  = { Text("— Not Mapped —", color = TEXT_DIM, fontSize = 13.sp) },
                onClick = { onSelected(""); expanded = false }
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(opt, color = Color.White, fontSize = 13.sp) },
                    onClick = { onSelected(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
fun ConfigButton(label: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = !loading && enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = GREEN),
        shape    = RoundedCornerShape(10.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun ResultBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A2A1A))
            .padding(12.dp)
    ) {
        Text(text, color = Color(0xFF80CB80), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
fun CreatedSheetCard(sheet: CreatedSheet, context: android.content.Context) {
    val linkToCopy = sheet.spreadsheetUrl.ifBlank {
        "https://docs.google.com/spreadsheets/d/${sheet.spreadsheetId}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2C1C))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(GREEN),
                    contentAlignment = Alignment.Center
                ) { Text("⊞", color = Color.White, fontSize = 14.sp) }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sheet.title.ifBlank { "Untitled" },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (sheet.createdAt.isNotBlank()) {
                        Text(sheet.createdAt, color = TEXT_DIM, fontSize = 11.sp)
                    }
                }
            }

            // ID chip
            Text(
                "ID: ${sheet.spreadsheetId.take(30)}…",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF222222))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            // URL text (truncated)
            Text(
                linkToCopy,
                color = Color(0xFF4DB6AC),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Copy link button
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Sheet URL", linkToCopy))
                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GREEN),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GREEN),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Copy Link", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Link", fontSize = 12.sp)
                }

                // Open in browser button
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkToCopy))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A2A)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
