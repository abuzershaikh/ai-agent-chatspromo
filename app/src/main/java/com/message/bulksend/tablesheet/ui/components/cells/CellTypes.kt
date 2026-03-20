package com.message.bulksend.tablesheet.ui.components.cells

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.CurrencyHelper
import com.message.bulksend.tablesheet.data.models.PriorityOption
import com.message.bulksend.tablesheet.ui.theme.TableTheme
import java.io.File

@Composable
fun TextInputCell(
    value: String,
    fieldType: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) { if (!isFocused) text = value }
    
    val backgroundColor = when {
        isFocused -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) Color(0xFF2196F3) else TableTheme.GRID_COLOR),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = text,
            onValueChange = { 
                text = it
                onValueChange(it)
            },
            textStyle = TextStyle(
                color = Color(0xFF333333),
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(Color(0xFF2196F3)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (fieldType) {
                    ColumnType.INTEGER -> KeyboardType.Number
                    ColumnType.PHONE -> KeyboardType.Phone
                    ColumnType.EMAIL -> KeyboardType.Email
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectCell(
    value: String,
    column: ColumnModel,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = column.selectOptions?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    
    val backgroundColor = when {
        expanded -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        Box(
            modifier = Modifier
                .width(cellWidth)
                .height(cellHeight)
                .background(backgroundColor)
                .border(1.dp, if (expanded) Color(0xFF2196F3) else TableTheme.GRID_COLOR)
                .menuAnchor()
                .clickable { expanded = true },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    color = Color(0xFF333333),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CheckboxCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val isChecked = value.equals("true", ignoreCase = true)
    
    val backgroundColor = when {
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, TableTheme.GRID_COLOR)
            .clickable { onValueChange(if (isChecked) "false" else "true") },
        contentAlignment = Alignment.Center
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onValueChange(if (it) "true" else "false") },
            colors = CheckboxDefaults.colors(checkedColor = TableTheme.HEADER_BG)
        )
    }
}

@Composable
fun EmailCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    TextInputCell(value, ColumnType.EMAIL, cellWidth, cellHeight, isRowSelected, onValueChange)
}

@Composable
fun PriorityCell(
    value: String,
    priorityOptions: List<PriorityOption>,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = priorityOptions.find { it.name == value }
    
    val backgroundColor = when {
        expanded -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, selectedOption?.color ?: TableTheme.GRID_COLOR)
            .clickable { expanded = true },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (selectedOption != null) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(selectedOption.color, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        selectedOption.name,
                        color = selectedOption.color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                } else {
                    Text(
                        value,
                        color = Color(0xFF333333),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            priorityOptions.forEach { option ->
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(option.color, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                option.name,
                                color = option.color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    onClick = {
                        onValueChange(option.name)
                        expanded = false
                    }
                )
            }
        }
    }
}