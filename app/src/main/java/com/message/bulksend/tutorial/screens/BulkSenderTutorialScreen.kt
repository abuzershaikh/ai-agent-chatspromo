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

data class TutorialStep(
    val title: String,
    val description: String,
    val icon: String
)

@Composable
fun BulkSenderTutorialScreen() {
    val steps = listOf(
        TutorialStep("📱 Create Contact List", "Go to Home and tap 'Campaign Contact List' to add your contacts from CSV, Excel, VCF or manually.", "1"),
        TutorialStep("📝 Setup Campaign", "Tap 'Send Message' and choose your campaign type - Text, Caption, Media, or Sheet based campaign.", "2"),
        TutorialStep("✍️ Write Message", "Compose your message and use #name# for personalization. You can also add images, videos or documents.", "3"),
        TutorialStep("⏱️ Set Delay", "Configure delay between messages (recommended 5-10 seconds) to avoid WhatsApp restrictions.", "4"),
        TutorialStep("🔐 Grant Permissions", "Allow Accessibility and Overlay permissions for the app to send messages automatically.", "5"),
        TutorialStep("🚀 Launch Campaign", "Tap 'Launch Campaign' and monitor progress using the floating overlay control panel.", "6"),
        TutorialStep("⏸️ Pause/Resume", "Use the floating panel to pause, resume or stop the campaign anytime.", "7"),
        TutorialStep("📊 Track Results", "Check Campaign Status and Reports to see delivery status and message logs.", "8")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "BulkSend - Mass Messaging",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Send personalized messages to multiple contacts at once",
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

@Composable
fun TutorialStepCard(step: TutorialStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                step.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                step.description,
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )
        }
    }
}
