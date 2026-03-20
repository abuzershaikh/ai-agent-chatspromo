package com.message.bulksend.tablesheet.ui.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.PriorityOption

private val GRID_COLOR = Color(0xFFBDBDBD)

@Composable
fun PriorityCell(
    value: String,
    priorityOptions: List<PriorityOption>,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    
    // Find the selected priority option
    val selectedOption = priorityOptions.find { it.name == value }
    
    val backgroundColor = when {
        selectedOption != null -> selectedOption.color.copy(alpha = 0.15f)
        showDropdown -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    val borderColor = when {
        selectedOption != null -> selectedOption.color
        showDropdown -> Color(0xFF2196F3)
        else -> GRID_COLOR
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, borderColor)
            .clickable { showDropdown = true },
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
                    // Priority color indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(selectedOption.color, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = selectedOption.name,
                        color = selectedOption.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Select priority...",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (selectedOption != null) selectedOption.color else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp))
        ) {
            if (priorityOptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No priorities available", color = Color.Gray) },
                    onClick = { showDropdown = false }
                )
            } else {
                priorityOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Color indicator
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(option.color, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    option.name,
                                    color = option.color,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.weight(1f))
                                if (value == option.name) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = option.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = { 
                            onValueChange(option.name)
                            showDropdown = false 
                        },
                        modifier = Modifier.background(
                            if (value == option.name) option.color.copy(alpha = 0.1f) else Color.Transparent
                        )
                    )
                }
            }
            
            // Clear option
            if (value.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFE5E7EB))
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Clear,
                                null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Clear", color = Color(0xFFEF4444))
                        }
                    },
                    onClick = { 
                        onValueChange("")
                        showDropdown = false 
                    }
                )
            }
        }
    }
}