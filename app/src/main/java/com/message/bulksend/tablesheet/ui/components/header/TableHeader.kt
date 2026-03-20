package com.message.bulksend.tablesheet.ui.components.header

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@Composable
fun TableHeader(
    tableName: String,
    rowCount: Int,
    columnCount: Int,
    onBackPressed: () -> Unit,
    onShowSheetInfo: () -> Unit,
    isLeadFormSheet: Boolean = false,
    onRefreshSync: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TableTheme.HEADER_BG,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    tableName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$rowCount rows • $columnCount columns",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            
            // Refresh button for LeadForm sheets
            if (isLeadFormSheet && onRefreshSync != null) {
                IconButton(onClick = onRefreshSync) {
                    Icon(Icons.Default.Refresh, "Sync Responses", tint = Color.White)
                }
            }
            
            IconButton(onClick = onShowSheetInfo) {
                Icon(Icons.Default.Info, "Sheet Info", tint = Color.White)
            }
        }
    }
}
