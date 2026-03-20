package com.message.bulksend.autorespond

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
/**
 * Data class for menu options
 */
data class MenuOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color,
    val onClick: () -> Unit = {}
)

/**
 * Menu Tab Content - Shows grid of reply options
 */
@Composable
fun MenuTabContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Reply Options - First Line
    val replyOptionsLine1 = listOf(
        MenuOption(
            icon = Icons.Default.WavingHand,
            title = "Welcome Message",
            description = "Greet first-time contacts",
            color = Color(0xFF22C55E),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.welcomemessage.WelcomeMessageActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.Key,
            title = "Keyword Reply",
            description = "Auto-reply with keywords",
            color = Color(0xFF00D4FF),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.keywordreply.KeywordReplyActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.Menu,
            title = "Menu Reply",
            description = "Interactive menu responses",
            color = Color(0xFFEC4899),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.menureply.MenuReplyActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.Camera,
            title = "Instagram Reply",
            description = "Auto-reply for Instagram DMs",
            color = Color(0xFFE4405F),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.instareply.InstagramAutoReplyActivity::class.java)
                context.startActivity(intent)
            }
        )
    )
    
    // Reply Options - Second Line
    val replyOptionsLine2 = listOf(
        MenuOption(
            icon = Icons.Default.AutoAwesome,
            title = "AI Reply",
            description = "Smart AI auto-replies",
            color = Color(0xFF8B5CF6),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.aireply.AIAutoReplyActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.Description,
            title = "Documents Reply",
            description = "Send documents automatically",
            color = Color(0xFFFF6B35),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.documentreply.DocumentReplyActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.TableChart,
            title = "Sheet Reply",
            description = "Reply from spreadsheet data",
            color = Color(0xFF10B981),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.sheetreply.SpreadsheetReplyActivity::class.java)
                context.startActivity(intent)
            }
        )
    )
    
    val otherOptions = listOf(
        MenuOption(
            icon = Icons.Default.Settings,
            title = "Auto Reply Settings",
            description = "Configure reply priority and behavior",
            color = Color(0xFFF59E0B),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.settings.AutoReplySettingsActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.PersonOff,
            title = "Exclude Numbers",
            description = "Block specific numbers from auto-reply",
            color = Color(0xFFEF4444),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.settings.ExcludeNumberActivity::class.java)
                context.startActivity(intent)
            }
        ),
        MenuOption(
            icon = Icons.Default.CloudSync,
            title = "Backup & Restore",
            description = "Backup your AutoRespond data to cloud",
            color = Color(0xFF06B6D4),
            onClick = {
                val intent = android.content.Intent(context, com.message.bulksend.autorespond.backup.AutoRespondBackupActivity::class.java)
                context.startActivity(intent)
            }
        )
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Expandable Reply Options Section
        ExpandableSectionCard(
            title = "Reply Options",
            icon = Icons.Default.ChatBubbleOutline
        ) {
            Column {
                // First Line - 4 Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    replyOptionsLine1.forEach { option ->
                        ReplyOptionCard(option = option)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Second Line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    replyOptionsLine2.forEach { option ->
                        ReplyOptionCard(option = option)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Expandable Settings Section
        ExpandableSectionCard(
            title = "Settings",
            icon = Icons.Default.Settings
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                otherOptions.forEach { option ->
                    SettingsOptionCard(option = option)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Expandable Section Card
 */
@Composable
fun ExpandableSectionCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFF2D3748)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            content()
        }
    }
}

/**
 * Reply Option Card - Campaign Manager Style (Horizontal Scrollable)
 * Matches MainActivity's CompactFeatureCard style exactly
 */
@Composable
fun ReplyOptionCard(option: MenuOption) {
    val gradient = listOf(option.color, option.color.copy(alpha = 0.7f))
    
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(120.dp)
            .clickable(onClick = option.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(gradient),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Icon Box - Same as CompactFeatureCard
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.title,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.height(10.dp))
                
                Text(
                    option.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Settings Option Card - List Style
 */
@Composable
fun SettingsOptionCard(option: MenuOption) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = option.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e)) // Slightly lighter background for nested card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        option.color.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.title,
                    tint = option.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Text(
                    option.description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


