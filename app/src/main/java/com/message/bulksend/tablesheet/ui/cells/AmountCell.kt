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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.CurrencyHelper

private val GRID_COLOR = Color(0xFFBDBDBD)

@Composable
fun AmountCell(
    value: String,
    column: ColumnModel,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) { if (!isFocused) text = value }
    
    // Parse currency options from column.selectOptions (format: "INR|₹|left")
    val currencyOptions = CurrencyHelper.parseCurrencyOptions(column.selectOptions)
    val symbol = currencyOptions?.second ?: "₹"
    val position = currencyOptions?.third ?: "left"

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
            .border(1.dp, if (isFocused) Color(0xFF2196F3) else GRID_COLOR),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol on left
            if (position == "left") {
                Text(
                    symbol,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
            }
            
            // Amount input
            BasicTextField(
                value = text,
                onValueChange = { newValue ->
                    // Only allow numbers and decimal
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    text = filtered
                    onValueChange(filtered)
                },
                textStyle = TextStyle(
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF2196F3)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
            )
            
            // Symbol on right
            if (position == "right") {
                Spacer(Modifier.width(4.dp))
                Text(
                    symbol,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}