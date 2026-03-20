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
fun LeadFormTutorialScreen() {
    val steps = listOf(
        TutorialStep("📝 Create New Form", "Tap 'Create Form' to start building your custom lead capture form.", "1"),
        TutorialStep("✏️ Add Form Fields", "Add fields like Name, Phone, Email, Address, Custom fields etc. as per your needs.", "2"),
        TutorialStep("🎨 Customize Design", "Choose colors, add your logo, and customize the form appearance.", "3"),
        TutorialStep("📋 Field Validation", "Set required fields and validation rules for phone, email etc.", "4"),
        TutorialStep("🔗 Generate Link", "Get a shareable link for your form that you can send to customers.", "5"),
        TutorialStep("📱 QR Code", "Generate QR code for your form - perfect for offline marketing.", "6"),
        TutorialStep("📥 View Submissions", "All form submissions are automatically saved and can be viewed anytime.", "7"),
        TutorialStep("📤 Export Data", "Export form submissions to CSV or Excel for further analysis.", "8"),
        TutorialStep("🔄 Auto-Add to CRM", "Enable auto-add to automatically create leads in CRM from form submissions.", "9")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Lead Form - Capture Leads",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Create beautiful forms to capture leads from anywhere",
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
