package com.message.bulksend.tablesheet.ui.components.cells

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.ui.theme.TableTheme
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MultiSelectCell(
    value: String,
    column: ColumnModel,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val options = parseSelectOptions(column.selectOptions)
    val selectedValues =
        remember(value) {
            value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
        }
    val display = selectedValues.joinToString(", ")

    val backgroundColor = when {
        overrideBackgroundColor != null -> overrideBackgroundColor
        expanded -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }

    Box {
        Row(
            modifier = Modifier
                .width(cellWidth)
                .height(cellHeight)
                .background(backgroundColor)
                .border(
                    1.dp,
                    when {
                        expanded -> Color(0xFF2196F3)
                        overrideBorderColor != null -> overrideBorderColor
                        else -> TableTheme.GRID_COLOR
                    }
                )
                .clickable { expanded = true }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = display.ifBlank { "Select..." },
                color = overrideTextColor ?: if (display.isBlank()) Color(0xFF9CA3AF) else Color(0xFF333333),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val checked = selectedValues.contains(option)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null
                            )
                            Text(option)
                        }
                    },
                    onClick = {
                        val next = selectedValues.toMutableSet()
                        if (!next.add(option)) next.remove(option)
                        onValueChange(next.joinToString(", "))
                    }
                )
            }
            if (selectedValues.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Clear") },
                    onClick = {
                        onValueChange("")
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun UrlCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isFocused) text = value
    }

    val backgroundColor = when {
        overrideBackgroundColor != null -> overrideBackgroundColor
        isFocused -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(
                1.dp,
                when {
                    isFocused -> Color(0xFF2196F3)
                    overrideBorderColor != null -> overrideBorderColor
                    else -> TableTheme.GRID_COLOR
                }
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            textStyle = TextStyle(
                color = overrideTextColor ?: Color(0xFF1D4ED8),
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(Color(0xFF2196F3)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused }
        )

        val normalized = normalizeWebLink(text)
        if (normalized != null) {
            Icon(
                Icons.Default.Link,
                contentDescription = "Open Link",
                tint = Color(0xFF2563EB),
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
                            )
                        }
                    }
            )
        }
    }
}

@Composable
fun MapCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(overrideBackgroundColor ?: if (isRowSelected) Color(0xFFE3F2FD).copy(alpha = 0.7f) else Color.White)
            .border(1.dp, overrideBorderColor ?: TableTheme.GRID_COLOR)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextInputCell(
            value = value,
            fieldType = ColumnType.MAP,
            cellWidth = cellWidth - 24.dp,
            cellHeight = cellHeight,
            isRowSelected = isRowSelected,
            onValueChange = onValueChange,
            overrideBackgroundColor = Color.Transparent,
            overrideTextColor = overrideTextColor,
            overrideBorderColor = Color.Transparent
        )
        Icon(
            Icons.Default.LocationOn,
            contentDescription = "Open Map",
            tint = Color(0xFFDC2626),
            modifier = Modifier
                .size(16.dp)
                .clickable {
                    if (value.isBlank()) return@clickable
                    val query = Uri.encode(value.trim())
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
                        )
                    }
                }
        )
    }
}

@Composable
fun MultiLineTextCell(
    value: String,
    fieldType: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    TextInputCell(
        value = value,
        fieldType = fieldType,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        isRowSelected = isRowSelected,
        onValueChange = onValueChange,
        overrideBackgroundColor = overrideBackgroundColor,
        overrideTextColor = overrideTextColor,
        overrideBorderColor = overrideBorderColor,
        singleLine = false
    )
}

@Composable
fun JsonCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    val isValidJson = remember(value) { isLikelyJson(value) }
    val borderColor =
        when {
            overrideBorderColor != null -> overrideBorderColor
            value.isBlank() -> TableTheme.GRID_COLOR
            isValidJson -> Color(0xFF22C55E)
            else -> Color(0xFFEF4444)
        }

    Row(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(overrideBackgroundColor ?: if (isRowSelected) Color(0xFFE3F2FD).copy(alpha = 0.7f) else Color.White)
            .border(1.dp, borderColor)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MultiLineTextCell(
            value = value,
            fieldType = ColumnType.JSON,
            cellWidth = cellWidth - 24.dp,
            cellHeight = cellHeight,
            isRowSelected = isRowSelected,
            onValueChange = onValueChange,
            overrideBackgroundColor = Color.Transparent,
            overrideTextColor = overrideTextColor ?: Color(0xFF1F2937),
            overrideBorderColor = Color.Transparent
        )
        Icon(
            Icons.Default.Code,
            contentDescription = "JSON",
            tint = if (isValidJson || value.isBlank()) Color(0xFF4F46E5) else Color(0xFFEF4444),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun FileCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    Row(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(overrideBackgroundColor ?: if (isRowSelected) Color(0xFFE3F2FD).copy(alpha = 0.7f) else Color.White)
            .border(1.dp, overrideBorderColor ?: TableTheme.GRID_COLOR)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextInputCell(
            value = value,
            fieldType = ColumnType.FILE,
            cellWidth = cellWidth - 24.dp,
            cellHeight = cellHeight,
            isRowSelected = isRowSelected,
            onValueChange = onValueChange,
            overrideBackgroundColor = Color.Transparent,
            overrideTextColor = overrideTextColor,
            overrideBorderColor = Color.Transparent
        )
        Icon(
            Icons.Default.AttachFile,
            contentDescription = "File",
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun FormulaCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    Row(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(overrideBackgroundColor ?: if (isRowSelected) Color(0xFFE3F2FD).copy(alpha = 0.7f) else Color.White)
            .border(1.dp, overrideBorderColor ?: TableTheme.GRID_COLOR)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Calculate,
            contentDescription = "Formula",
            tint = Color(0xFF0284C7),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        TextInputCell(
            value = value,
            fieldType = ColumnType.FORMULA,
            cellWidth = cellWidth - 24.dp,
            cellHeight = cellHeight,
            isRowSelected = isRowSelected,
            onValueChange = { raw ->
                val normalized = if (raw.isBlank() || raw.startsWith("=")) raw else "=$raw"
                onValueChange(normalized)
            },
            overrideBackgroundColor = Color.Transparent,
            overrideTextColor = overrideTextColor ?: Color(0xFF0284C7),
            overrideBorderColor = Color.Transparent
        )
    }
}

private fun normalizeWebLink(raw: String): String? {
    val value = raw.trim()
    if (value.isBlank()) return null
    return when {
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("www.", ignoreCase = true) -> "https://$value"
        else -> null
    }
}

private fun isLikelyJson(raw: String): Boolean {
    val value = raw.trim()
    if (value.isBlank()) return true
    if (!(value.startsWith("{") || value.startsWith("["))) return false
    return runCatching {
        if (value.startsWith("{")) JSONObject(value) else JSONArray(value)
        true
    }.getOrElse { false }
}
