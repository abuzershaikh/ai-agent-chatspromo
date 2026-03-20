package com.message.bulksend.tablesheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HeaderBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetInfoScreen(
    tableName: String,
    rowCount: Int,
    columnCount: Int,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = HeaderBlue,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Sheet Guide",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A2E)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "\"$tableName\" • $rowCount rows • $columnCount columns",
                fontSize = 13.sp,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(16.dp))
            
            // Quick Tips Section
            SectionTitle("Quick Tips")
            Spacer(Modifier.height(12.dp))
            
            InfoTipItem(
                icon = Icons.Default.Edit,
                title = "Edit Header Name",
                description = "Tap on any column header to edit its name and type. Long press for more options like delete."
            )
            
            InfoTipItem(
                icon = Icons.Default.TouchApp,
                title = "Edit Cells",
                description = "Tap any cell to edit its value. Different column types have different input methods (text, date picker, dropdown, etc.)."
            )
            
            InfoTipItem(
                icon = Icons.Default.ViewColumn,
                title = "Add Column",
                description = "Tap the green + button on the right side of column headers, or use 'Column' button at bottom bar."
            )
            
            InfoTipItem(
                icon = Icons.Default.TableRows,
                title = "Add Rows",
                description = "Tap the green + button at bottom left corner, or use 'Row' button to add multiple rows at once."
            )
            
            InfoTipItem(
                icon = Icons.Default.Delete,
                title = "Delete Row/Column",
                description = "Long press on row number (left side) or column header to see delete option in menu."
            )
            
            InfoTipItem(
                icon = Icons.Default.Settings,
                title = "Row Height",
                description = "Use Settings button at bottom to adjust row height - Small (36dp), Medium (44dp), Large (56dp), Extra Large (72dp)."
            )
            
            InfoTipItem(
                icon = Icons.Default.SwapVert,
                title = "Reorder Columns",
                description = "Use 'Column' button at bottom → drag the handle icon to reorder columns as needed."
            )
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(16.dp))
            
            // Column Types Section
            SectionTitle("Available Column Types")
            Spacer(Modifier.height(12.dp))
            
            ColumnTypeItem(Icons.Default.TextFields, "Text", "Simple text input for any content")
            ColumnTypeItem(Icons.Default.Numbers, "Number", "Numeric values only")
            ColumnTypeItem(Icons.Default.CalendarMonth, "Date", "Date picker with calendar")
            ColumnTypeItem(Icons.Default.Schedule, "Time", "Time picker")
            ColumnTypeItem(Icons.Default.Email, "Email", "Email input with @ keyboard")
            ColumnTypeItem(Icons.Default.Phone, "Phone", "Phone number input")
            ColumnTypeItem(Icons.Default.CheckBox, "Checkbox", "True/False toggle")
            ColumnTypeItem(Icons.Default.ArrowDropDown, "Select", "Dropdown with custom options")
            ColumnTypeItem(Icons.Default.Mic, "Audio", "Record audio directly in cell")
            ColumnTypeItem(Icons.Default.CurrencyRupee, "Amount", "Currency with symbol (100+ currencies)")
            
            Spacer(Modifier.height(20.dp))
            
            // Got it button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got it!", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF374151)
    )
}

@Composable
private fun InfoTipItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(HeaderBlue.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = HeaderBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2937))
            Text(description, fontSize = 12.sp, color = Color(0xFF6B7280), lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ColumnTypeItem(
    icon: ImageVector,
    name: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = HeaderBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151), modifier = Modifier.width(80.dp))
        Text("- $description", fontSize = 12.sp, color = Color.Gray)
    }
}
