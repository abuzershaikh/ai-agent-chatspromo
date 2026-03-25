package com.message.bulksend.tablesheet.ui.components.sheets

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableSettingsSheet(
    rowHeight: Float,
    onRowHeightChange: (Float) -> Unit,
    columnCount: Int,
    frozenColumnCount: Int,
    onFrozenColumnCountChange: (Int) -> Unit,
    onDismiss: () -> Unit
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
                "Table Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            
            Spacer(Modifier.height(24.dp))

            Text(
                "Frozen Columns",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$frozenColumnCount of $columnCount columns",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (frozenColumnCount > 0) {
                            onFrozenColumnCountChange(frozenColumnCount - 1)
                        }
                    }
                ) {
                    Icon(Icons.Default.Remove, "Decrease frozen columns", tint = TableTheme.HEADER_BG)
                }

                Slider(
                    value = frozenColumnCount.toFloat(),
                    onValueChange = { onFrozenColumnCountChange(it.toInt()) },
                    valueRange = 0f..columnCount.coerceAtLeast(0).toFloat(),
                    steps = (columnCount - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = TableTheme.HEADER_BG,
                        activeTrackColor = TableTheme.HEADER_BG
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (frozenColumnCount < columnCount) {
                            onFrozenColumnCountChange(frozenColumnCount + 1)
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, "Increase frozen columns", tint = TableTheme.HEADER_BG)
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // Row Height Section
            Text(
                "Row Height",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
            Spacer(Modifier.height(8.dp))
            
            Text(
                "${rowHeight.toInt()} dp",
                fontSize = 14.sp,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Row height slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (rowHeight > 30f) onRowHeightChange(rowHeight - 5f) 
                }) {
                    Icon(Icons.Default.Remove, "Decrease", tint = TableTheme.HEADER_BG)
                }
                
                Slider(
                    value = rowHeight,
                    onValueChange = onRowHeightChange,
                    valueRange = 30f..100f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = TableTheme.HEADER_BG,
                        activeTrackColor = TableTheme.HEADER_BG
                    ),
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { 
                    if (rowHeight < 100f) onRowHeightChange(rowHeight + 5f) 
                }) {
                    Icon(Icons.Default.Add, "Increase", tint = TableTheme.HEADER_BG)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Preset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Small" to 36f,
                    "Medium" to 44f,
                    "Large" to 56f,
                    "XL" to 72f
                ).forEach { (label, height) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRowHeightChange(height) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (rowHeight == height) TableTheme.HEADER_BG else Color(0xFFF3F4F6),
                        border = BorderStroke(
                            1.dp, 
                            if (rowHeight == height) TableTheme.HEADER_BG else Color(0xFFE5E7EB)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (rowHeight == height) Color.White else Color(0xFF374151)
                            )
                            Text(
                                "${height.toInt()}dp",
                                fontSize = 10.sp,
                                color = if (rowHeight == height) Color.White.copy(alpha = 0.8f) else Color.Gray
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Preview
            Text(
                "Preview",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
            Spacer(Modifier.height(8.dp))
            
            // Preview row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight.dp)
                    .border(1.dp, TableTheme.GRID_COLOR, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .background(
                            TableTheme.HEADER_BG, 
                            RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("1", color = Color.White, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.White),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "Sample cell content",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = Color(0xFF333333),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
