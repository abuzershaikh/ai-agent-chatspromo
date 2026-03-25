package com.message.bulksend.tablesheet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.ConditionalFormatRuleModel
import com.message.bulksend.tablesheet.data.models.FilterViewModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.ui.components.cells.CellFormatStyle
import com.message.bulksend.tablesheet.ui.components.dialogs.TableDialogManager
import com.message.bulksend.tablesheet.ui.components.header.TableColumnHeaders
import com.message.bulksend.tablesheet.ui.components.header.TableHeader
import com.message.bulksend.tablesheet.extractor.ui.ExtractorToolSheet
import com.message.bulksend.tablesheet.ui.components.sheets.FilterAndFormatSheet
import com.message.bulksend.tablesheet.ui.components.sheets.FilterConditionMode
import com.message.bulksend.tablesheet.ui.components.sheets.FilterLogic
import com.message.bulksend.tablesheet.ui.components.sheets.PivotSummaryCard
import com.message.bulksend.tablesheet.ui.components.sheets.TableFilterCondition
import com.message.bulksend.tablesheet.ui.components.sheets.TableFilterConfig
import com.message.bulksend.tablesheet.ui.components.sheets.TablePivotRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

@Composable
fun TableEditorScreen(
    tableName: String,
    columns: List<ColumnModel>,
    rows: List<RowModel>,
    cellsMap: Map<Pair<Long, Long>, String>,
    filterViews: List<FilterViewModel> = emptyList(),
    conditionalRules: List<ConditionalFormatRuleModel> = emptyList(),
    frozenColumnCount: Int = 0,
    onBackPressed: () -> Unit,
    onAddColumn: (name: String, type: String, width: Float, selectOptions: String?) -> Unit,
    onAddRows: (Int) -> Unit,
    onCellValueChange: (rowId: Long, columnId: Long, value: String) -> Unit,
    onPasteGridData: (startRowId: Long, startColumnId: Long, rawText: String) -> Unit,
    onDeleteRow: (rowId: Long) -> Unit,
    onDeleteColumn: (columnId: Long) -> Unit,
    onUpdateColumn: ((columnId: Long, name: String, type: String, width: Float, selectOptions: String?) -> Unit)? = null,
    onReorderColumns: ((List<ColumnModel>) -> Unit)? = null,
    onFrozenColumnCountChange: ((Int) -> Unit)? = null,
    onSaveFilterView: ((name: String, filtersJson: String, sortColumnId: Long?, sortDirection: String, isDefault: Boolean) -> Unit)? = null,
    onDeleteFilterView: ((Long) -> Unit)? = null,
    onSaveConditionalFormatRule: ((columnId: Long, ruleType: String, criteria: String, backgroundColor: String?, textColor: String?, priority: Int, enabled: Boolean) -> Unit)? = null,
    onDeleteConditionalFormatRule: ((Long) -> Unit)? = null,
    pivotSummaryCard: PivotSummaryCard? = null,
    onRunPivotRequest: ((TablePivotRequest) -> Unit)? = null,
    onShareSheet: (() -> Unit)? = null,
    extractorProcessing: Boolean = false,
    extractorProgress: Float = 0f,
    extractorStatus: String = "",
    onExtractorPickRequest: (() -> Unit)? = null,
    isLeadFormSheet: Boolean = false,
    onRefreshSync: (() -> Unit)? = null
) {
    var showAddRowsSheet by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf<ColumnModel?>(null) }
    var showNewColumnEditor by remember { mutableStateOf(false) }
    var showColumnManager by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSheetInfo by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var scanningCell by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var autoAddEnabled by remember { mutableStateOf(true) }
    var rowHeight by remember { mutableStateOf(44f) }
    var showRowDataFillDialog by remember { mutableStateOf(false) }
    var fillDataRowId by remember { mutableStateOf<Long?>(null) }
    var showFilterFormatSheet by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }
    var activeFilterConfig by remember { mutableStateOf<TableFilterConfig?>(null) }
    var previewColumnWidths by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    
    val horizontalScrollState = rememberScrollState()
    val bottomToolbarScrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val normalizedFrozenCount = frozenColumnCount.coerceIn(0, columns.size)
    val displayColumns by remember(columns, previewColumnWidths) {
        derivedStateOf {
            columns.map { column ->
                previewColumnWidths[column.id]?.let { previewWidth ->
                    column.copy(width = previewWidth)
                } ?: column
            }
        }
    }

    LaunchedEffect(columns) {
        val existingIds = columns.map { it.id }.toSet()
        previewColumnWidths =
            previewColumnWidths.filter { (columnId, previewWidth) ->
                val latest = columns.firstOrNull { it.id == columnId } ?: return@filter false
                columnId in existingIds && abs(latest.width - previewWidth) > 0.001f
            }
    }

    val visibleRows by remember(rows, columns, cellsMap, activeFilterConfig) {
        derivedStateOf {
            applyFilterAndSort(
                rows = rows,
                columns = columns,
                cellsMap = cellsMap,
                config = activeFilterConfig
            )
        }
    }

    val cellFormatStyles by remember(visibleRows, conditionalRules, cellsMap) {
        derivedStateOf {
            buildCellFormatMap(
                visibleRows = visibleRows,
                cellsMap = cellsMap,
                rules = conditionalRules
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            TableHeader(
                tableName = tableName,
                rowCount = visibleRows.size,
                columnCount = columns.size,
                onBackPressed = onBackPressed,
                onShowSheetInfo = { showSheetInfo = true },
                isLeadFormSheet = isLeadFormSheet,
                onRefreshSync = onRefreshSync
            )

            // Column Headers Row
            TableColumnHeaders(
                columns = displayColumns,
                frozenColumnCount = normalizedFrozenCount,
                horizontalScrollState = horizontalScrollState,
                onDeleteColumn = onDeleteColumn,
                onEditColumn = { column -> editingColumn = column },
                onUpdateColumn = onUpdateColumn,
                onPreviewColumnWidthChange = { columnId, newWidth ->
                    previewColumnWidths =
                        previewColumnWidths.toMutableMap().apply {
                            put(columnId, newWidth)
                        }
                },
                onCommitColumnWidthChange = { columnId, newWidth ->
                    previewColumnWidths =
                        previewColumnWidths.toMutableMap().apply {
                            put(columnId, newWidth)
                        }

                    val sourceColumn = columns.firstOrNull { it.id == columnId }
                    if (sourceColumn != null) {
                        onUpdateColumn?.invoke(
                            sourceColumn.id,
                            sourceColumn.name,
                            sourceColumn.type,
                            newWidth,
                            sourceColumn.selectOptions
                        )
                    }
                },
                onAddColumn = { showNewColumnEditor = true }
            )

            // Data Rows - Add bottom padding for toolbar and make it fill remaining space
            Box(modifier = Modifier.weight(1f)) {
                TableDataRows(
                    rows = visibleRows,
                    columns = displayColumns,
                    frozenColumnCount = normalizedFrozenCount,
                    cellsMap = cellsMap,
                    cellStyles = cellFormatStyles,
                    rowHeight = rowHeight,
                    horizontalScrollState = horizontalScrollState,
                    listState = listState,
                    onCellValueChange = onCellValueChange,
                    onDeleteRow = onDeleteRow,
                    onFillRowData = { rowId ->
                        fillDataRowId = rowId
                        showRowDataFillDialog = true
                    },
                    onPasteGridData = onPasteGridData,
                    onAddRows = { showAddRowsSheet = true }
                )
            }
        }

        // Fixed Bottom Bar - positioned at bottom of screen
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color(0xFFF5F5F5),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(bottomToolbarScrollState)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BottomButton(Icons.Default.ViewColumn, "Column") { showColumnManager = true }
                    BottomButton(Icons.Default.Add, "Row") { showAddRowsSheet = true }
                    BottomButton(Icons.Default.FilterList, "Filter") { showFilterFormatSheet = true }
                    BottomButton(Icons.Default.Handyman, "Tools") { showToolsSheet = true }
                    BottomButton(Icons.Default.Settings, "Settings") { showSettingsSheet = true }
                    BottomButton(Icons.Default.Share, "Share") { onShareSheet?.invoke() }
                }
            }
        }

        if (extractorProcessing) {
            ExtractorProcessingBanner(
                progress = extractorProgress,
                status = extractorStatus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp, start = 12.dp, end = 12.dp)
            )
        }
    }

    // Handle all dialogs and screens
    TableDialogManager(
        showAddRowsSheet = showAddRowsSheet,
        onDismissAddRowsSheet = { showAddRowsSheet = false },
        onAddRows = { count -> onAddRows(count); showAddRowsSheet = false },
        
        editingColumn = editingColumn,
        onDismissEditColumn = { editingColumn = null },
        onUpdateColumn = onUpdateColumn,
        onDeleteColumn = onDeleteColumn,
        
        showNewColumnEditor = showNewColumnEditor,
        onDismissNewColumnEditor = { showNewColumnEditor = false },
        onAddColumn = onAddColumn,
        
        showColumnManager = showColumnManager,
        onDismissColumnManager = { showColumnManager = false },
        onOpenNewColumnEditor = { showNewColumnEditor = true },
        onEditColumnFromManager = { column ->
            showColumnManager = false
            editingColumn = column
        },
        columns = columns,
        onReorderColumns = onReorderColumns,
        
        showSettingsSheet = showSettingsSheet,
        onDismissSettingsSheet = { showSettingsSheet = false },
        rowHeight = rowHeight,
        onRowHeightChange = { rowHeight = it },
        columnCount = columns.size,
        frozenColumnCount = normalizedFrozenCount,
        onFrozenColumnCountChange = { count ->
            onFrozenColumnCountChange?.invoke(count.coerceIn(0, columns.size))
        },
        
        showSheetInfo = showSheetInfo,
        onDismissSheetInfo = { showSheetInfo = false },
        tableName = tableName,
        
        showBarcodeScanner = showBarcodeScanner,
        onDismissBarcodeScanner = { 
            showBarcodeScanner = false
            scanningCell = null
        },
        scanningCell = scanningCell,
        rows = rows,
        autoAddEnabled = autoAddEnabled,
        onAutoAddChanged = { autoAddEnabled = it },
        onCellValueChange = onCellValueChange,
        
        showRowDataFillDialog = showRowDataFillDialog,
        onDismissRowDataFillDialog = { 
            showRowDataFillDialog = false
            fillDataRowId = null
        },
        fillDataRowId = fillDataRowId,
        currentData = cellsMap
    )

    if (showFilterFormatSheet) {
        FilterAndFormatSheet(
            columns = columns,
            filterViews = filterViews,
            conditionalRules = conditionalRules,
            activeFilterConfig = activeFilterConfig,
            pivotSummaryCard = pivotSummaryCard,
            onApplyFilterConfig = { activeFilterConfig = it },
            onApplySavedFilterView = { view ->
                activeFilterConfig = decodeFilterConfig(view)
            },
            onSaveFilterView = { name, config, isDefault ->
                onSaveFilterView?.invoke(
                    name,
                    encodeFilterConfig(config),
                    config.sortColumnId,
                    config.sortDirection,
                    isDefault
                )
            },
            onDeleteFilterView = { viewId ->
                onDeleteFilterView?.invoke(viewId)
            },
            onAddConditionalRule = { columnId, ruleType, criteria, backgroundColor, textColor, priority, enabled ->
                onSaveConditionalFormatRule?.invoke(
                    columnId,
                    ruleType,
                    criteria,
                    backgroundColor,
                    textColor,
                    priority,
                    enabled
                )
            },
            onDeleteConditionalRule = { ruleId ->
                onDeleteConditionalFormatRule?.invoke(ruleId)
            },
            onRunPivot = { request ->
                onRunPivotRequest?.invoke(request)
            },
            onDismiss = { showFilterFormatSheet = false }
        )
    }

    if (showToolsSheet) {
        ExtractorToolSheet(
            onDismiss = { showToolsSheet = false },
            onExtractorClick = {
                showToolsSheet = false
                onExtractorPickRequest?.invoke()
            }
        )
    }
}

@Composable
private fun ExtractorProcessingBanner(
    progress: Float,
    status: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "extractor_processing_progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFFE0F2FE), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF0EA5E9),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Extractor Processing",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = status.ifBlank { "Processing content..." },
                    color = Color(0xFF475569),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0EA5E9),
                    trackColor = Color(0xFFE2E8F0)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun BottomButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp, 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            label,
            tint = Color(0xFF666666),
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}

private fun applyFilterAndSort(
    rows: List<RowModel>,
    columns: List<ColumnModel>,
    cellsMap: Map<Pair<Long, Long>, String>,
    config: TableFilterConfig?
): List<RowModel> {
    if (config == null || !config.isActive) return rows

    val activeConditions =
        config.conditions.filter { condition ->
            condition.mode.equals(FilterConditionMode.IS_EMPTY, ignoreCase = true) ||
                condition.mode.equals(FilterConditionMode.NOT_EMPTY, ignoreCase = true) ||
                condition.value.isNotBlank()
        }

    val filtered =
        rows.filter { row ->
            if (activeConditions.isEmpty()) return@filter true
            val conditionResults =
                activeConditions.map { condition ->
                    val values =
                        if (condition.columnId == null) {
                            columns.map { column -> cellsMap[row.id to column.id].orEmpty() }
                        } else {
                            listOf(cellsMap[row.id to condition.columnId].orEmpty())
                        }
                    values.any { value ->
                        matchesFilterCondition(
                            valueRaw = value,
                            condition = condition
                        )
                    }
                }

            if (config.conditionOperator.equals(FilterLogic.OR, ignoreCase = true)) {
                conditionResults.any { it }
            } else {
                conditionResults.all { it }
            }
        }

    val sortColumnId = config.sortColumnId ?: return filtered
    val sortColumnType = columns.firstOrNull { it.id == sortColumnId }?.type
    val sorted =
        filtered.sortedWith { left, right ->
            compareCellValues(
                leftRaw = cellsMap[left.id to sortColumnId].orEmpty(),
                rightRaw = cellsMap[right.id to sortColumnId].orEmpty(),
                columnTypeRaw = sortColumnType
            )
        }

    return if (config.sortDirection.equals("DESC", ignoreCase = true)) {
        sorted.reversed()
    } else {
        sorted
    }
}

private fun matchesFilterCondition(
    valueRaw: String,
    condition: TableFilterCondition
): Boolean {
    val value = valueRaw.trim()
    val needle = condition.value.trim()
    return when (condition.mode.trim().uppercase(Locale.ROOT)) {
        FilterConditionMode.EXACT -> value.equals(needle, ignoreCase = true)
        FilterConditionMode.STARTS_WITH -> needle.isNotBlank() && value.startsWith(needle, ignoreCase = true)
        FilterConditionMode.ENDS_WITH -> needle.isNotBlank() && value.endsWith(needle, ignoreCase = true)
        FilterConditionMode.GREATER_THAN -> {
            val left = value.toDoubleOrNull()
            val right = needle.toDoubleOrNull()
            left != null && right != null && left > right
        }
        FilterConditionMode.LESS_THAN -> {
            val left = value.toDoubleOrNull()
            val right = needle.toDoubleOrNull()
            left != null && right != null && left < right
        }
        FilterConditionMode.IS_EMPTY -> value.isBlank()
        FilterConditionMode.NOT_EMPTY -> value.isNotBlank()
        else -> needle.isNotBlank() && value.contains(needle, ignoreCase = true)
    }
}

private fun compareCellValues(
    leftRaw: String,
    rightRaw: String,
    columnTypeRaw: String?
): Int {
    val left = leftRaw.trim()
    val right = rightRaw.trim()
    if (left.isEmpty() && right.isEmpty()) return 0
    if (left.isEmpty()) return -1
    if (right.isEmpty()) return 1

    val normalizedType = columnTypeRaw?.trim()?.uppercase(Locale.ROOT).orEmpty()
    when (normalizedType) {
        ColumnType.INTEGER, ColumnType.DECIMAL, ColumnType.AMOUNT -> {
            val leftNum = left.replace(",", "").replace(Regex("[^0-9+\\-.]"), "").toDoubleOrNull()
            val rightNum = right.replace(",", "").replace(Regex("[^0-9+\\-.]"), "").toDoubleOrNull()
            if (leftNum != null && rightNum != null) return leftNum.compareTo(rightNum)
        }
        ColumnType.CHECKBOX -> {
            val leftBool = left.equals("true", ignoreCase = true)
            val rightBool = right.equals("true", ignoreCase = true)
            return leftBool.compareTo(rightBool)
        }
        ColumnType.DATE, "DATEONLY", ColumnType.DATETIME, ColumnType.TIME, "TIME" -> {
            val leftTime = parseDateSortValue(left)
            val rightTime = parseDateSortValue(right)
            if (leftTime != null && rightTime != null) return leftTime.compareTo(rightTime)
        }
    }

    val leftNum = left.replace(",", "").replace(Regex("[^0-9+\\-.]"), "").toDoubleOrNull()
    val rightNum = right.replace(",", "").replace(Regex("[^0-9+\\-.]"), "").toDoubleOrNull()
    if (leftNum != null && rightNum != null) return leftNum.compareTo(rightNum)

    return left.compareTo(right, ignoreCase = true)
}

private fun parseDateSortValue(raw: String): Long? {
    val token = raw.trim()
    if (token.isBlank()) return null
    token.toLongOrNull()?.let { epoch ->
        if (epoch > 0L) return epoch
    }
    val patterns =
        listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "yyyy/MM/dd",
            "yyyyMMdd",
            "HH:mm",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
    patterns.forEach { pattern ->
        runCatching {
            java.text.SimpleDateFormat(pattern, Locale.getDefault()).apply {
                isLenient = false
            }.parse(token)
        }.getOrNull()?.let { parsed ->
            return parsed.time
        }
    }
    return null
}

private fun buildCellFormatMap(
    visibleRows: List<RowModel>,
    cellsMap: Map<Pair<Long, Long>, String>,
    rules: List<ConditionalFormatRuleModel>
): Map<Pair<Long, Long>, CellFormatStyle> {
    if (visibleRows.isEmpty() || rules.isEmpty()) return emptyMap()
    val sortedRules = rules.filter { it.enabled }.sortedBy { it.priority }
    if (sortedRules.isEmpty()) return emptyMap()

    val styles = mutableMapOf<Pair<Long, Long>, CellFormatStyle>()
    visibleRows.forEach { row ->
        sortedRules.forEach { rule ->
            val key = row.id to rule.columnId
            if (key in styles) return@forEach

            val cellValue = cellsMap[key].orEmpty()
            if (matchesRule(cellValue, rule.ruleType, rule.criteria)) {
                val bg = parseColor(rule.backgroundColor)
                val text = parseColor(rule.textColor)
                val border = bg?.copy(alpha = 0.65f)
                styles[key] =
                    CellFormatStyle(
                        backgroundColor = bg,
                        textColor = text,
                        borderColor = border
                    )
            }
        }
    }
    return styles
}

private fun matchesRule(valueRaw: String, ruleTypeRaw: String, criteriaRaw: String): Boolean {
    val value = valueRaw.trim()
    val criteria = criteriaRaw.trim()
    return when (ruleTypeRaw.trim().uppercase(Locale.ROOT)) {
        "CONTAINS" -> criteria.isNotBlank() && value.contains(criteria, ignoreCase = true)
        "EQUALS" -> value.equals(criteria, ignoreCase = true)
        "NOT_EQUALS" -> !value.equals(criteria, ignoreCase = true)
        "GREATER_THAN" -> {
            val left = value.toDoubleOrNull()
            val right = criteria.toDoubleOrNull()
            left != null && right != null && left > right
        }
        "LESS_THAN" -> {
            val left = value.toDoubleOrNull()
            val right = criteria.toDoubleOrNull()
            left != null && right != null && left < right
        }
        "IS_EMPTY" -> value.isBlank()
        "NOT_EMPTY" -> value.isNotBlank()
        else -> criteria.isNotBlank() && value.contains(criteria, ignoreCase = true)
    }
}

private fun parseColor(raw: String?): Color? {
    val token = raw?.trim().orEmpty()
    if (token.isBlank()) return null
    return runCatching {
        Color(android.graphics.Color.parseColor(token))
    }.getOrNull()
}

private fun encodeFilterConfig(config: TableFilterConfig): String {
    val conditionsJson =
        JSONArray().apply {
            config.conditions.forEach { condition ->
                put(
                    JSONObject()
                        .put("columnId", condition.columnId ?: JSONObject.NULL)
                        .put("mode", condition.mode)
                        .put("value", condition.value)
                )
            }
        }

    return JSONObject()
        .put("v", 2)
        .put("conditions", conditionsJson)
        .put("conditionOperator", config.conditionOperator)
        .put("sortColumnId", config.sortColumnId ?: JSONObject.NULL)
        .put("sortDirection", config.sortDirection)
        .toString()
}

private fun decodeFilterConfig(view: FilterViewModel): TableFilterConfig? {
    return runCatching {
        val json = JSONObject(view.filtersJson.ifBlank { "{}" })
        val conditions =
            parseConditionArray(json.optJSONArray("conditions")).ifEmpty {
                parseLegacyCondition(json)
            }
        val rawOperator =
            json.optString("conditionOperator", json.optString("operator", FilterLogic.AND))
                .trim()
                .uppercase(Locale.ROOT)
        val conditionOperator =
            if (rawOperator == FilterLogic.OR) FilterLogic.OR else FilterLogic.AND
        val jsonSortColumnId =
            if (json.has("sortColumnId") && !json.isNull("sortColumnId")) json.optLong("sortColumnId") else null

        val normalizedSortDirection =
            view.sortDirection.ifBlank {
                json.optString("sortDirection", "ASC")
            }.trim().uppercase(Locale.ROOT).let { direction ->
                if (direction == "DESC") "DESC" else "ASC"
            }

        TableFilterConfig(
            conditions = conditions,
            conditionOperator = conditionOperator,
            sortColumnId = view.sortColumnId ?: jsonSortColumnId,
            sortDirection = normalizedSortDirection
        )
    }.getOrNull()
}

private fun parseConditionArray(array: JSONArray?): List<TableFilterCondition> {
    if (array == null) return emptyList()
    val conditions = mutableListOf<TableFilterCondition>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val columnId =
            if (item.has("columnId") && !item.isNull("columnId")) item.optLong("columnId") else null
        val mode = item.optString("mode", FilterConditionMode.CONTAINS).ifBlank { FilterConditionMode.CONTAINS }
        val value = item.optString("value", "")
        conditions +=
            TableFilterCondition(
                columnId = columnId,
                mode = mode,
                value = value
            )
    }
    return conditions
}

private fun parseLegacyCondition(json: JSONObject): List<TableFilterCondition> {
    val query = json.optString("query").trim()
    if (query.isBlank()) return emptyList()
    val columnId =
        if (json.has("columnId") && !json.isNull("columnId")) json.optLong("columnId") else null
    val mode = json.optString("mode", FilterConditionMode.CONTAINS).ifBlank { FilterConditionMode.CONTAINS }
    return listOf(
        TableFilterCondition(
            columnId = columnId,
            mode = mode,
            value = query
        )
    )
}
