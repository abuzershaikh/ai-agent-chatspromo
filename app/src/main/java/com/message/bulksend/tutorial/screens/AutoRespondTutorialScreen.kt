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
fun AutoRespondTutorialScreen() {
    val steps = listOf(
        TutorialStep("🤖 Enable AutoRespond", "Go to AutoRespond tab and toggle ON the auto-reply feature to start responding automatically.", "1"),
        TutorialStep("💬 Create Reply Templates", "Add custom reply messages for different scenarios - greetings, away messages, business hours etc.", "2"),
        TutorialStep("⏰ Set Schedule", "Configure active hours when auto-reply should work. Set different messages for business hours and off-hours.", "3"),
        TutorialStep("🎯 Keyword Triggers", "Set up keyword-based responses. When someone sends a specific word, send a custom reply.", "4"),
        TutorialStep("📋 Contact Filters", "Choose to reply to all contacts, only saved contacts, or specific groups.", "5"),
        TutorialStep("⏱️ Reply Delay", "Set a natural delay before sending auto-reply to make it look more human.", "6"),
        TutorialStep("🔔 Notification Settings", "Configure whether to show notifications for auto-replied messages.", "7"),
        TutorialStep("📊 View Statistics", "Check how many messages were auto-replied and their success rate.", "8")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "AutoRespond - Smart Auto Reply",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Automatically reply to WhatsApp messages 24/7",
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
