package com.message.bulksend.tools

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Quickreply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.message.bulksend.ui.theme.BulksendTestTheme

class ToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                ToolScreen(onBackPressed = { finish() })
            }
        }
    }
}

data class Tool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val isComingSoon: Boolean = false
)

@Composable
fun ToolScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    
    // Typewriter effect state
    var displayedText by remember { mutableStateOf("") }
    val fullText = "Boost your business growth"
    
    // Typewriter animation
    LaunchedEffect(Unit) {
        while (true) {
            displayedText = ""
            for (char in fullText) {
                displayedText += char
                delay(80)
            }
            delay(3000)
        }
    }
    
    // Featured tools for first row (horizontal scroll)
    val featuredTools = listOf(
        Tool(
            id = "auto_respond",
            title = "AutoRespond",
            description = "Auto reply messages • Smart responses • Save time • 24/7 active",
            icon = Icons.Outlined.Quickreply,
            gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)) // Purple-Indigo
        ),
        Tool(
            id = "chats_promo_crm",
            title = "ChatsPromo CRM",
            description = "Manage customers • Track leads • Sales pipeline • Analytics",
            icon = Icons.Default.SupportAgent,
            gradient = listOf(Color(0xFF06B6D4), Color(0xFF0891B2)) // Cyan-Teal
        )
    )
    
    // Other tools for second row
    val otherTools = listOf(
        Tool(
            id = "file_opener",
            title = "File Opener",
            description = "Open TXT � MP3 � MP4 � Route files to the right viewer automatically",
            icon = Icons.Default.FolderOpen,
            gradient = listOf(Color(0xFF0EA5E9), Color(0xFF2563EB))
        ),
        Tool(
            id = "invoice_maker",
            title = "Invoice Maker",
            description = "Create invoices • Add logo • PDF/PNG export • Share instantly",
            icon = Icons.Default.Receipt,
            gradient = listOf(Color(0xFF10B981), Color(0xFF059669)) // Emerald-Green
        ),
        Tool(
            id = "sheet",
            title = "Sheet",
            description = "Import/Export leads • Excel • CSV • Bulk data • Analysis tools",
            icon = Icons.Default.TableChart,
            gradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)) // Blue
        ),
        Tool(
            id = "pdf_viewer",
            title = "PDF Viewer",
            description = "View PDFs • Password support • Zoom • Share • Open any PDF file",
            icon = Icons.Default.PictureAsPdf,
            gradient = listOf(Color(0xFFEF4444), Color(0xFFDC2626)) // Red
        )
    )
    
    // Beautiful aurora-like background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF1A1A2E),
            Color(0xFF0D0D1A)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with gradient background (reduced size)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF16213E),
                                Color(0xFF0F3460)
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
                ) {
                    // Back Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackPressed,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Color.White.copy(alpha = 0.15f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            "Growth Tools",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Colorful Typewriter Text
                    ColorfulTypewriterText(displayedText = displayedText)
                }
            }
            
            // First Row - Featured Tools Section
            Spacer(modifier = Modifier.height(20.dp))
            
            // Section Header with divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))
                            ),
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "✨ Featured",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6).copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                
                featuredTools.forEach { tool ->
                    GlowingToolCard(
                        tool = tool,
                        onClick = {
                            when (tool.id) {
                                "auto_respond" -> {
                                    context.startActivity(Intent(context, com.message.bulksend.autorespond.AutoRespondActivity::class.java))
                                }
                                "chats_promo_crm" -> {
                                    context.startActivity(Intent(context, com.message.bulksend.leadmanager.LeadManagerActivity::class.java))
                                }
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            // Second Row - More Tools Section
            Spacer(modifier = Modifier.height(24.dp))
            
            // Section Header with divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF3B82F6))
                            ),
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "🛠️ More Tools",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF10B981).copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                
                otherTools.forEach { tool ->
                    GlowingToolCard(
                        tool = tool,
                        onClick = {
                            when (tool.id) {
                                "file_opener" -> {
                                    context.startActivity(Intent(context, com.message.bulksend.tools.fileopener.FileOpenerActivity::class.java))
                                }
                                "invoice_maker" -> {
                                    context.startActivity(Intent(context, com.message.bulksend.tools.invoicemaker.InvoiceMakerActivity::class.java))
                                }
                                "sheet" -> {
                                    context.startActivity(Intent(context, com.message.bulksend.tablesheet.TableSheetActivity::class.java))
                                }
                                "pdf_viewer" -> {
                                    context.startActivity(Intent(context, com.message.bulksend.pdfviewer.PdfListActivity::class.java))
                                }
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}

@Composable
fun GlowingToolCard(
    tool: Tool,
    onClick: () -> Unit
) {
    val primaryColor = tool.gradient.first()
    
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(140.dp)
            .shadow(
                elevation = if (!tool.isComingSoon) 12.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = primaryColor.copy(alpha = 0.4f),
                spotColor = primaryColor.copy(alpha = 0.3f)
            )
            .clickable(enabled = !tool.isComingSoon, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = if (tool.isComingSoon) {
                            listOf(Color(0xFF374151), Color(0xFF1F2937))
                        } else {
                            tool.gradient
                        }
                    )
                )
        ) {
            // Coming Soon badge
            if (tool.isComingSoon) {
                Surface(
                    color = Color(0xFFFF6B9D),
                    shape = RoundedCornerShape(bottomStart = 12.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        "Soon",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon with white background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.White.copy(alpha = if (tool.isComingSoon) 0.1f else 0.2f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        tool.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = if (tool.isComingSoon) 0.5f else 1f),
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Title
                Text(
                    tool.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (tool.isComingSoon) 0.5f else 1f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Description - Auto-scrolling
                ScrollingDescription(
                    text = tool.description,
                    isDark = true
                )
            }
        }
    }
}

@Composable
fun ScrollingDescription(text: String, isDark: Boolean = false) {
    val scrollState = rememberScrollState()
    
    LaunchedEffect(Unit) {
        while (true) {
            scrollState.animateScrollTo(
                scrollState.maxValue,
                animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
            )
            delay(1500)
            scrollState.animateScrollTo(
                0,
                animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
            )
            delay(1500)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF94A3B8),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun ColorfulTypewriterText(displayedText: String) {
    val wordColors = listOf(
        Color(0xFF6366F1), // Indigo - Boost
        Color(0xFF10B981), // Emerald - your
        Color(0xFFF59E0B), // Amber - business
        Color(0xFFEC4899)  // Pink - growth
    )
    
    val words = displayedText.split(" ")
    
    Text(
        text = buildAnnotatedString {
            words.forEachIndexed { index, word ->
                withStyle(
                    style = SpanStyle(
                        color = wordColors[index % wordColors.size],
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                ) {
                    append(word)
                }
                if (index < words.size - 1) {
                    append(" ")
                }
            }
        },
        lineHeight = 24.sp
    )
}
