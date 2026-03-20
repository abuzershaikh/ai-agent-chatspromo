package com.message.bulksend.tablesheet.ui.components.header

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@Composable
fun RowNumberCell(
    index: Int,
    rowHeight: Float = 44f,
    isSelected: Boolean = false,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .width(TableTheme.ROW_NUMBER_WIDTH)
            .height(rowHeight.dp)
            .background(if (isSelected) Color(0xFF1565C0) else TableTheme.HEADER_BG)
            .border(1.dp, if (isSelected) Color(0xFF0D47A1) else TableTheme.GRID_COLOR)
            .clickable { onSelect() }
            .pointerInput(Unit) { 
                detectTapGestures(onLongPress = { showMenu = true }) 
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "$index",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Row", color = Color.Red) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )
        }
    }
}