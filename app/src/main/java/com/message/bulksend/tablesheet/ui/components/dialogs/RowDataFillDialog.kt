package com.message.bulksend.tablesheet.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.FieldTypes
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowDataFillDialog(
    rowId: Long,
    columns: List<ColumnModel>,
    currentData: Map<Pair<Long, Long>, String>,
    onDismiss: () -> Unit,
    onSave: (Map<Long, String>) -> Unit
) {
    val formData = remember(rowId, columns) {
        mutableStateMapOf<Long, String>().apply {
            columns.forEach { column ->
                val currentValue = currentData[Pair(rowId, column.id)] ?: ""
                put(column.id, currentValue)
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fill Row Data",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(formData.toMap()) },
                        colors = ButtonDefaults.buttonColors(containerColor = TableTheme.HEADER_BG)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(16.dp))
            
            // Form fields
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = columns.size,
                    key = { index -> columns[index].id }
                ) { index ->
                    val column = columns[index]
                    RowDataField(
                        column = column,
                        value = formData[column.id] ?: "",
                        onValueChange = { newValue ->
                            formData[column.id] = newValue
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowDataField(
    column: ColumnModel,
    value: String,
    onValueChange: (String) -> Unit
) {
    val typeConfig = FieldTypes.getConfig(column.type)
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Field label with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                typeConfig.icon,
                contentDescription = null,
                tint = typeConfig.color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                column.name,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
        }
        
        // Simple text field for now - can be enhanced later
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = when (column.type) {
                    ColumnType.INTEGER -> KeyboardType.Number
                    ColumnType.PHONE -> KeyboardType.Phone
                    ColumnType.EMAIL -> KeyboardType.Email
                    else -> KeyboardType.Text
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TableTheme.HEADER_BG,
                focusedLabelColor = TableTheme.HEADER_BG
            )
        )
    }
}