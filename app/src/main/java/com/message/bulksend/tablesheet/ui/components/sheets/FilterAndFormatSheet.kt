package com.message.bulksend.tablesheet.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PivotTableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ConditionalFormatRuleModel
import com.message.bulksend.tablesheet.data.models.FilterViewModel
import java.util.Locale

data class TableFilterCondition(
    val columnId: Long? = null,
    val mode: String = FilterConditionMode.CONTAINS,
    val value: String = ""
)

data class TableFilterConfig(
    val conditions: List<TableFilterCondition> = emptyList(),
    val conditionOperator: String = FilterLogic.AND,
    val sortColumnId: Long? = null,
    val sortDirection: String = "ASC"
) {
    val isActive: Boolean
        get() = conditions.isNotEmpty() || sortColumnId != null
}

object FilterLogic {
    const val AND = "AND"
    const val OR = "OR"
}

object FilterConditionMode {
    const val CONTAINS = "CONTAINS"
    const val EXACT = "EXACT"
    const val STARTS_WITH = "STARTS_WITH"
    const val ENDS_WITH = "ENDS_WITH"
    const val GREATER_THAN = "GREATER_THAN"
    const val LESS_THAN = "LESS_THAN"
    const val IS_EMPTY = "IS_EMPTY"
    const val NOT_EMPTY = "NOT_EMPTY"

    fun all() = listOf(CONTAINS, EXACT, STARTS_WITH, ENDS_WITH, GREATER_THAN, LESS_THAN, IS_EMPTY, NOT_EMPTY)
}

data class TablePivotRequest(
    val groupByColumnId: Long,
    val operation: String,
    val valueColumnId: Long? = null
)

data class PivotRowSummary(
    val label: String,
    val metric: String
)

data class PivotSummaryCard(
    val success: Boolean,
    val title: String,
    val message: String,
    val groupByColumn: String,
    val metric: String,
    val rows: List<PivotRowSummary>,
    val totalGroups: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterAndFormatSheet(
    columns: List<ColumnModel>,
    filterViews: List<FilterViewModel>,
    conditionalRules: List<ConditionalFormatRuleModel>,
    activeFilterConfig: TableFilterConfig?,
    pivotSummaryCard: PivotSummaryCard?,
    onApplyFilterConfig: (TableFilterConfig?) -> Unit,
    onApplySavedFilterView: (FilterViewModel) -> Unit,
    onSaveFilterView: (name: String, config: TableFilterConfig, isDefault: Boolean) -> Unit,
    onDeleteFilterView: (Long) -> Unit,
    onAddConditionalRule: (
        columnId: Long,
        ruleType: String,
        criteria: String,
        backgroundColor: String?,
        textColor: String?,
        priority: Int,
        enabled: Boolean
    ) -> Unit,
    onDeleteConditionalRule: (Long) -> Unit,
    onRunPivot: (TablePivotRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var conditions by remember(activeFilterConfig, columns) {
        mutableStateOf(activeFilterConfig?.conditions?.ifEmpty { listOf(defaultCondition(columns)) } ?: listOf(defaultCondition(columns)))
    }
    var conditionOperator by remember(activeFilterConfig) { mutableStateOf(activeFilterConfig?.conditionOperator ?: FilterLogic.AND) }
    var sortColumnId by remember(activeFilterConfig) { mutableStateOf(activeFilterConfig?.sortColumnId) }
    var sortDirection by remember(activeFilterConfig) { mutableStateOf(activeFilterConfig?.sortDirection ?: "ASC") }
    var saveViewName by remember { mutableStateOf("") }
    var markAsDefault by remember { mutableStateOf(false) }

    var ruleColumnId by remember(columns) { mutableStateOf(columns.firstOrNull()?.id) }
    var ruleType by remember { mutableStateOf("CONTAINS") }
    var ruleCriteria by remember { mutableStateOf("") }
    var ruleBg by remember { mutableStateOf("#FFF8E1") }
    var ruleText by remember { mutableStateOf("#1F2937") }
    var rulePriority by remember { mutableStateOf(0) }
    var ruleEnabled by remember { mutableStateOf(true) }

    var pivotGroupBy by remember(columns) { mutableStateOf(columns.firstOrNull()?.id) }
    var pivotOperation by remember { mutableStateOf("COUNT") }
    var pivotValueColumn by remember(columns) { mutableStateOf(columns.firstOrNull()?.id) }

    val activeConditions =
        conditions.map { it.copy(value = it.value.trim()) }.filter {
            it.mode == FilterConditionMode.IS_EMPTY || it.mode == FilterConditionMode.NOT_EMPTY || it.value.isNotBlank()
        }
    val config =
        TableFilterConfig(
            conditions = activeConditions,
            conditionOperator = conditionOperator,
            sortColumnId = sortColumnId,
            sortDirection = sortDirection
        )
    val conditionError = validateConditions(activeConditions)
    val ruleError = validateRule(ruleType, ruleCriteria, ruleBg, ruleText)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Filter + Format + Pivot", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterList, null, tint = Color(0xFF1976D2))
                Spacer(Modifier.width(8.dp))
                Text("Advanced Multi-Condition Filter", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))

            conditions.forEachIndexed { idx, condition ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Condition ${idx + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (conditions.size > 1) {
                                IconButton(onClick = { conditions = conditions.filterIndexed { index, _ -> index != idx } }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                                }
                            }
                        }
                        ColumnDropdown("Column", condition.columnId, columns, true) { selected ->
                            conditions = conditions.toMutableList().also { it[idx] = it[idx].copy(columnId = selected) }
                        }
                        Spacer(Modifier.height(6.dp))
                        StringDropdown("Match", condition.mode, FilterConditionMode.all()) { mode ->
                            conditions = conditions.toMutableList().also { it[idx] = it[idx].copy(mode = mode) }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (requiresValue(condition.mode)) {
                            OutlinedTextField(
                                value = condition.value,
                                onValueChange = { newValue ->
                                    conditions = conditions.toMutableList().also { it[idx] = it[idx].copy(value = newValue) }
                                },
                                label = { Text("Value") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            Text("No value needed for ${condition.mode.lowercase(Locale.ROOT)}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Button(onClick = { conditions = conditions + defaultCondition(columns) }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add Condition")
            }
            Spacer(Modifier.height(8.dp))
            StringDropdown("Combine", conditionOperator, listOf(FilterLogic.AND, FilterLogic.OR)) { conditionOperator = it }
            Spacer(Modifier.height(8.dp))
            ColumnDropdown("Sort Column (Optional)", sortColumnId, columns, true) { sortColumnId = it }
            Spacer(Modifier.height(8.dp))
            StringDropdown("Sort Direction", sortDirection, listOf("ASC", "DESC")) { sortDirection = it }

            if (conditionError != null) {
                Spacer(Modifier.height(8.dp))
                Text(conditionError, color = Color(0xFFB00020), fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onApplyFilterConfig(if (config.isActive) config else null) },
                    enabled = conditionError == null,
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
                TextButton(
                    onClick = {
                        conditions = listOf(defaultCondition(columns))
                        sortColumnId = null
                        sortDirection = "ASC"
                        conditionOperator = FilterLogic.AND
                        onApplyFilterConfig(null)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = saveViewName, onValueChange = { saveViewName = it }, label = { Text("Save as Filter View") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Set as default")
                Switch(checked = markAsDefault, onCheckedChange = { markAsDefault = it })
            }
            Button(
                onClick = {
                    if (saveViewName.isNotBlank() && config.isActive && conditionError == null) {
                        onSaveFilterView(saveViewName.trim(), config, markAsDefault)
                        saveViewName = ""
                        markAsDefault = false
                    }
                },
                enabled = saveViewName.isNotBlank() && config.isActive && conditionError == null
            ) { Text("Save View") }

            if (filterViews.isNotEmpty()) {
                filterViews.forEach { view ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onApplySavedFilterView(view) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(view.name, modifier = Modifier.weight(1f), fontWeight = if (view.isDefault) FontWeight.SemiBold else FontWeight.Normal)
                        IconButton(onClick = { onDeleteFilterView(view.id) }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = Color(0xFF6A1B9A))
                Spacer(Modifier.width(8.dp))
                Text("Conditional Formatting", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            ColumnDropdown("Target Column", ruleColumnId, columns, false) { ruleColumnId = it }
            Spacer(Modifier.height(8.dp))
            StringDropdown("Rule Type", ruleType, listOf("CONTAINS", "EQUALS", "NOT_EQUALS", "GREATER_THAN", "LESS_THAN", "IS_EMPTY", "NOT_EMPTY")) { ruleType = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = ruleCriteria, onValueChange = { ruleCriteria = it }, label = { Text("Criteria") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = ruleType != "IS_EMPTY" && ruleType != "NOT_EMPTY")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = ruleBg, onValueChange = { ruleBg = it }, label = { Text("BG #hex") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = ruleText, onValueChange = { ruleText = it }, label = { Text("Text #hex") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(6.dp))
            ColorPresetRow(ruleBg) { ruleBg = it }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Priority: $rulePriority")
                Row {
                    TextButton(onClick = { if (rulePriority > 0) rulePriority-- }) { Text("-") }
                    TextButton(onClick = { if (rulePriority < 50) rulePriority++ }) { Text("+") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled")
                Switch(checked = ruleEnabled, onCheckedChange = { ruleEnabled = it })
            }
            RulePreview(columnName = columns.firstOrNull { it.id == ruleColumnId }?.name ?: "Column", ruleType = ruleType, criteria = ruleCriteria, bgHex = ruleBg, textHex = ruleText, enabled = ruleEnabled)
            if (ruleError != null) {
                Spacer(Modifier.height(6.dp))
                Text(ruleError, color = Color(0xFFB00020), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val col = ruleColumnId
                    if (col != null) {
                        onAddConditionalRule(col, ruleType, ruleCriteria.trim(), ruleBg.ifBlank { null }, ruleText.ifBlank { null }, rulePriority, ruleEnabled)
                        ruleCriteria = ""
                    }
                },
                enabled = ruleColumnId != null && ruleError == null
            ) { Text("Add Rule") }

            conditionalRules.forEach { rule ->
                val colName = columns.firstOrNull { it.id == rule.columnId }?.name ?: "Column ${rule.columnId}"
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatch(rule.backgroundColor)
                    Spacer(Modifier.width(8.dp))
                    Text("$colName - ${rule.ruleType} (${rule.criteria.ifBlank { "-" }})", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    IconButton(onClick = { onDeleteConditionalRule(rule.id) }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PivotTableChart, null, tint = Color(0xFF0D47A1))
                Spacer(Modifier.width(8.dp))
                Text("SHEET_PIVOT Summary Card", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            ColumnDropdown("Group By", pivotGroupBy, columns, false) { pivotGroupBy = it }
            Spacer(Modifier.height(8.dp))
            StringDropdown("Operation", pivotOperation, listOf("COUNT", "SUM", "AVG", "MIN", "MAX")) { pivotOperation = it }
            if (pivotOperation != "COUNT") {
                Spacer(Modifier.height(8.dp))
                ColumnDropdown("Value Column", pivotValueColumn, columns, false) { pivotValueColumn = it }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val group = pivotGroupBy
                    if (group != null) {
                        onRunPivot(TablePivotRequest(groupByColumnId = group, operation = pivotOperation, valueColumnId = if (pivotOperation == "COUNT") null else pivotValueColumn))
                    }
                },
                enabled = pivotGroupBy != null && (pivotOperation == "COUNT" || pivotValueColumn != null)
            ) { Text("Run SHEET_PIVOT") }

            if (pivotSummaryCard != null) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = if (pivotSummaryCard.success) Color(0xFFEAF4FF) else Color(0xFFFFEBEE))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(pivotSummaryCard.title, fontWeight = FontWeight.Bold)
                        Text(pivotSummaryCard.message, fontSize = 12.sp, color = Color(0xFF374151))
                        if (pivotSummaryCard.success) {
                            Spacer(Modifier.height(6.dp))
                            AssistChip(onClick = {}, enabled = false, label = { Text("Group: ${pivotSummaryCard.groupByColumn}") })
                            AssistChip(onClick = {}, enabled = false, label = { Text("Metric: ${pivotSummaryCard.metric}") }, modifier = Modifier.padding(top = 4.dp))
                            pivotSummaryCard.rows.take(8).forEach { row ->
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("${row.label}: ${row.metric}", fontSize = 12.sp) },
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdown(
    label: String,
    selectedId: Long?,
    columns: List<ColumnModel>,
    includeAll: Boolean,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = if (includeAll && selectedId == null) "All Columns" else columns.firstOrNull { it.id == selectedId }?.name ?: "Select"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includeAll) {
                DropdownMenuItem(text = { Text("All Columns") }, onClick = { onSelected(null); expanded = false })
            }
            columns.forEach { column ->
                DropdownMenuItem(text = { Text(column.name) }, onClick = { onSelected(column.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringDropdown(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun RulePreview(columnName: String, ruleType: String, criteria: String, bgHex: String, textHex: String, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AssistChip(onClick = {}, enabled = false, label = { Text("$columnName $ruleType ${criteria.ifBlank { "-" }}", fontSize = 12.sp) })
        AssistChip(onClick = {}, enabled = false, label = { Text(if (enabled) "ENABLED" else "DISABLED", fontSize = 12.sp) })
    }
    Spacer(Modifier.height(4.dp))
    Card(colors = CardDefaults.cardColors(containerColor = parseColor(bgHex) ?: Color(0xFFF5F5F5)), modifier = Modifier.fillMaxWidth()) {
        Text("Preview Text", modifier = Modifier.padding(8.dp), color = parseColor(textHex) ?: Color(0xFF1F2937), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ColorPresetRow(selected: String, onSelect: (String) -> Unit) {
    val palette = listOf("#FFF8E1", "#E8F5E9", "#E3F2FD", "#FCE4EC", "#F3E5F5", "#FFF3E0", "#ECEFF1")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        palette.forEach { hex ->
            val color = parseColor(hex) ?: Color(0xFFE5E7EB)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, CircleShape)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(hex: String?) {
    Box(modifier = Modifier.size(16.dp).background(parseColor(hex) ?: Color(0xFFE5E7EB), RoundedCornerShape(4.dp)))
}

private fun defaultCondition(columns: List<ColumnModel>): TableFilterCondition {
    return TableFilterCondition(columnId = columns.firstOrNull()?.id)
}

private fun requiresValue(mode: String): Boolean {
    return mode != FilterConditionMode.IS_EMPTY && mode != FilterConditionMode.NOT_EMPTY
}

private fun validateConditions(conditions: List<TableFilterCondition>): String? {
    conditions.forEachIndexed { index, condition ->
        if (!requiresValue(condition.mode)) return@forEachIndexed
        if (condition.value.isBlank()) return "Condition ${index + 1}: value required."
        if ((condition.mode == FilterConditionMode.GREATER_THAN || condition.mode == FilterConditionMode.LESS_THAN) &&
            condition.value.toDoubleOrNull() == null
        ) {
            return "Condition ${index + 1}: numeric value required."
        }
    }
    return null
}

private fun validateRule(ruleType: String, criteria: String, bgHex: String, textHex: String): String? {
    val needsCriteria = ruleType != "IS_EMPTY" && ruleType != "NOT_EMPTY"
    if (needsCriteria && criteria.trim().isBlank()) return "Criteria is required for $ruleType."
    if ((ruleType == "GREATER_THAN" || ruleType == "LESS_THAN") && criteria.trim().toDoubleOrNull() == null) {
        return "Criteria must be numeric for $ruleType."
    }
    if (bgHex.isNotBlank() && parseColor(bgHex) == null) return "Invalid background color hex."
    if (textHex.isNotBlank() && parseColor(textHex) == null) return "Invalid text color hex."
    return null
}

private fun parseColor(raw: String?): Color? {
    val token = raw?.trim().orEmpty()
    if (token.isBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(token)) }.getOrNull()
}
