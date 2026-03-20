package com.message.bulksend.tutorial

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tutorial.screens.*
import com.message.bulksend.ui.theme.BulksendTestTheme

class VideoTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                VideoTutorialScreen(onBackPressed = { finish() })
            }
        }
    }
}

data class TutorialVideo(
    val title: String,
    val description: String,
    val youtubeUrl: String,
    val color: Color
)

data class TutorialTab(
    val title: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTutorialScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf(
        TutorialTab("BulkSender", Color(0xFF6366F1)),
        TutorialTab("AutoRespond", Color(0xFF8B5CF6)),
        TutorialTab("CRM", Color(0xFF10B981)),
        TutorialTab("Lead Form", Color(0xFF3B82F6)),
        TutorialTab("Sheets", Color(0xFFF59E0B)),
        TutorialTab("Invoice", Color(0xFFEC4899))
    )
    
    val videos = listOf(
        TutorialVideo("BulkSend Tutorial", "Learn how to send bulk messages", "https://www.youtube.com/watch?v=U-8aOvpT_sQ", Color(0xFF6366F1)),
        TutorialVideo("AutoRespond Setup", "Setup automatic replies", "https://youtu.be/aQ6FbbGd5dw?si=Lx-9au8WWOWkd9Wt", Color(0xFF8B5CF6)),
        TutorialVideo("CRM Guide", "WhatsApp CRM & Automation", "https://youtu.be/h20AE5j6Ilo?si=jANVov8bYZgHnL8a", Color(0xFF10B981)),
        TutorialVideo("Lead Form Creation", "Survey, Feedback, Registration Forms", "https://youtu.be/Nvv7VjsTrIc?si=KJm7UdAwZ-OxHz3I", Color(0xFF3B82F6)),
        TutorialVideo("Sheets Tutorial", "Data management with sheets", "https://www.youtube.com/watch?v=U-8aOvpT_sQ", Color(0xFFF59E0B)),
        TutorialVideo("Invoice Maker", "Create professional invoices", "https://www.youtube.com/watch?v=U-8aOvpT_sQ", Color(0xFFEC4899))
    )
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF1E293B),
            Color(0xFF334155)
        )
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Video Tutorials",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B)
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Video Card at Top
                VideoCard(
                    video = videos[selectedTab],
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videos[selectedTab].youtubeUrl))
                        context.startActivity(intent)
                    }
                )
                
                // Tab Row
                ScrollableTabRow(
                    selectedTab = selectedTab,
                    tabs = tabs,
                    onTabSelected = { selectedTab = it }
                )
                
                // Content based on selected tab
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8FAFC))
                ) {
                    when (selectedTab) {
                        0 -> BulkSenderTutorialScreen()
                        1 -> AutoRespondTutorialScreen()
                        2 -> CRMTutorialScreen()
                        3 -> LeadFormTutorialScreen()
                        4 -> SheetsTutorialScreen()
                        5 -> InvoiceMakerTutorialScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(video: TutorialVideo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            video.color.copy(alpha = 0.3f),
                            Color(0xFF0F172A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Play Button
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.Red
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    video.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    video.description,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Tap to watch on YouTube",
                    fontSize = 12.sp,
                    color = video.color
                )
            }
        }
    }
}

@Composable
fun ScrollableTabRow(
    selectedTab: Int,
    tabs: List<TutorialTab>,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            TabChip(
                title = tab.title,
                color = tab.color,
                isSelected = selectedTab == index,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
fun TabChip(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) color else Color(0xFF334155),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
        )
    }
}
