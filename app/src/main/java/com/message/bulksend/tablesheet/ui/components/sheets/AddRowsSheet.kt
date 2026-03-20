package com.message.bulksend.tablesheet.ui.components.sheets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRowsSheetContent(
    onDismiss: () -> Unit,
    onAddRows: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Add Blank Rows",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(1, 5, 10, 50, 100).forEach { n ->
                    Surface(
                        modifier = Modifier.clickable { onAddRows(n) },
                        shape = RoundedCornerShape(20.dp),
                        color = TableTheme.HEADER_BG.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, TableTheme.HEADER_BG)
                    ) {
                        Text(
                            "$n",
                            modifier = Modifier.padding(20.dp, 10.dp),
                            color = TableTheme.HEADER_BG,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}