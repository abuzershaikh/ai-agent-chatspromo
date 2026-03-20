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
fun CRMTutorialScreen() {
    val steps = listOf(
        TutorialStep("👥 Add Leads", "Tap + button to add new leads manually or import from CSV/Excel files.", "1"),
        TutorialStep("📊 Dashboard Overview", "View your lead statistics - total leads, new, contacted, qualified, converted etc.", "2"),
        TutorialStep("🏷️ Manage Tags", "Create custom tags to categorize your leads - Hot, Warm, Cold, VIP etc.", "3"),
        TutorialStep("📁 Lead Sources", "Track where your leads come from - Website, WhatsApp, Referral, Social Media etc.", "4"),
        TutorialStep("📦 Products/Services", "Add your products or services to link with leads for better tracking.", "5"),
        TutorialStep("📅 Follow-up Schedule", "Set follow-up reminders and never miss a lead. Get notifications for pending follow-ups.", "6"),
        TutorialStep("📝 Lead Notes", "Add notes and interaction history for each lead to track conversations.", "7"),
        TutorialStep("☁️ Cloud Sync", "Backup your CRM data to cloud and sync across devices.", "8"),
        TutorialStep("📈 Reports", "View detailed reports on lead conversion, source performance, and team activity.", "9")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "ChatsPromo CRM - Lead Management",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Manage your leads, track follow-ups and grow your business",
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
