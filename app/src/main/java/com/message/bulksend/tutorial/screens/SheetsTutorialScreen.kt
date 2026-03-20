package com.message.bulksend.tutorial.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SheetsTutorialScreen() {
    val steps = listOf(
        TutorialStep("📊 Create New Sheet", "Tap + to create a new spreadsheet for your data management needs.", "1"),
        TutorialStep("📥 Import Data", "Import data from CSV, Excel (.xlsx), or Google Sheets directly.", "2"),
        TutorialStep("✏️ Edit Cells", "Tap any cell to edit. Long press for more options like copy, paste, delete.", "3"),
        TutorialStep("➕ Add Columns/Rows", "Easily add new columns or rows to expand your data.", "4"),
        TutorialStep("🔍 Search & Filter", "Use search to find specific data. Apply filters to view specific records.", "5"),
        TutorialStep("📤 Export Data", "Export your sheet to CSV or Excel format for sharing or backup.", "6"),
        TutorialStep("📱 Send to Campaign", "Use sheet data directly for bulk messaging campaigns.", "7"),
        TutorialStep("🔄 Sync with CRM", "Sync sheet data with CRM to keep leads updated.", "8"),
        TutorialStep("📋 Templates", "Use pre-built templates for common use cases like contact list, inventory etc.", "9")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "ChatsPromo Sheets - Data Management",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Manage your data with powerful spreadsheet features",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(steps.size) { index ->
            TutorialStepCard(steps[index])
        }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
