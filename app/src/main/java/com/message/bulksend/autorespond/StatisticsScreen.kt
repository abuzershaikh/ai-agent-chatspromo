package com.message.bulksend.autorespond

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Statistics Tab Content - Shows message logs report and leads management
 */
@Composable
fun StatisticsTabContent(notifications: List<NotificationData>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            "Analytics & Management",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // 1. View Detailed Report Card - Top
        AutoReplyReportCardCompact(onClick = {
            val intent = Intent(context, MessageLogsActivity::class.java)
            context.startActivity(intent)
        })
        
        // 2. Instagram Analytics Card - New
        InstagramAnalyticsCard(onClick = {
            val intent = Intent(context, com.message.bulksend.autorespond.instastats.InstagramStatsActivity::class.java)
            context.startActivity(intent)
        })
        
        // 3. Leads Management Card
        LeadsManagementCard(onClick = {
            val intent = Intent(context, com.message.bulksend.leadmanager.LeadManagerActivity::class.java)
            context.startActivity(intent)
        })
    }
}

/**
 * Instagram Analytics Card
 */
@Composable
fun InstagramAnalyticsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFE4405F).copy(alpha = 0.15f),
                            Color(0xFF1a1a2e)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Instagram Icon with Gradient
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE4405F),
                                Color(0xFFF56040),
                                Color(0xFFFCAF45)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "IG",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Instagram Analytics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View Instagram DM auto-reply statistics",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            // Button
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE4405F)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "View",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Leads Management Card
 */
@Composable
fun LeadsManagementCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.1f),
                            Color(0xFF1a1a2e)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Color(0xFF10B981).copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Leads Management",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Manage leads from user conversations",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            // Button
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Open",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
