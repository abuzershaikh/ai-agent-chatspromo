package com.message.bulksend.tablesheet.ui.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnType

private val GRID_COLOR = Color(0xFFBDBDBD)

@Composable
fun TextInputCell(
    value: String, 
    columnType: String, 
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
        modifier = Modifier.width(cellWidth).height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) Color(0xFF2196F3) else GRID_COLOR),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            textStyle = TextStyle(color = Color(0xFF333333), fontSize = 13.sp),
            cursorBrush = SolidColor(Color(0xFF2196F3)),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (columnType) {
                    ColumnType.INTEGER, ColumnType.AMOUNT -> KeyboardType.Number
                    ColumnType.PHONE -> KeyboardType.Phone
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}