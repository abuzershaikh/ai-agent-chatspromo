package com.message.bulksend.tutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class FaqActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaqTheme {
                FaqScreen(onBackPressed = { finish() })
            }
        }
    }
}

@Composable
fun FaqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF4F46E5),
            background = Color(0xFFF8FAFC),
            surface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBackPressed: () -> Unit) {
    val faqList = remember { getFaqList() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Frequently Asked Questions",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Complete guide to using the app",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
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
                    containerColor = Color(0xFF4F46E5)
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(faqList) { index, faqItem ->
                FaqItemCard(faqItem = faqItem)
            }
            
            // Bottom spacing for edge-to-edge
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FaqItemCard(faqItem: FaqItem) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "rotation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Question Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = faqItem.question,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationAngle)
                )
            }
            
            // Answer with Animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = Color(0xFFE2E8F0),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = faqItem.answer,
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

data class FaqItem(
    val question: String,
    val answer: String
)

fun getFaqList(): List<FaqItem> {
    return listOf(
        FaqItem(
            "What is Chatspromo?",
            "Chatspromo is a bulk WhatsApp messaging app that allows you to send text, image, or video messages to multiple contacts automatically using campaigns."
        ),
        FaqItem(
            "What is a campaign?",
            "A campaign is a message batch you create to send one type of message (Text, Caption, Text + Media, or Sheet) to a selected contact group."
        ),
        FaqItem(
            "How do I start sending many messages at once?",
            "From the Home screen → tap Send Message → Start → choose a campaign type → select your contact group → type your message → tap Launch Campaign. The app will automatically send all messages one by one."
        ),
        FaqItem(
            "What are the different campaign types?",
            "You can choose from four styles:\n\n📝 Text Campaign – text only\n🖼️ Caption Campaign – caption for media\n📷 Text + Media – text with image/video\n📊 Sheet Campaign – send using spreadsheet-style lists"
        ),
        FaqItem(
            "How can I import contacts?",
            "You can import contacts from CSV, VCF, XLSX, Google Sheets, TXT, or enter them manually. Give your list a Group Name, save it, and reuse it in future campaigns."
        ),
        FaqItem(
            "What is the country code and why is it required?",
            "WhatsApp requires each number to have a valid country code (like +91 for India). Without it, messages cannot be delivered."
        ),
        FaqItem(
            "What does #name# mean in the message box?",
            "#name# is a personalization tag. When sending, it automatically replaces #name# with each contact's actual name from your contact group."
        ),
        FaqItem(
            "What is \"Delay Between Messages\"?",
            "It's the time gap between sending two messages (e.g., 5 seconds). This keeps your account safe and prevents WhatsApp restrictions."
        ),
        FaqItem(
            "What is Accessibility Permission and why is it needed?",
            "Accessibility permission allows Chatspromo to automatically perform tap and send actions on WhatsApp on your behalf. Without it, the app cannot send messages automatically."
        ),
        FaqItem(
            "What is Overlay Permission?",
            "Overlay permission lets Chatspromo display a floating control panel (on top of other apps). You can use this panel to Pause, Stop, or Resume campaigns anytime while sending."
        ),
        FaqItem(
            "Can I pause or resume my campaign?",
            "Yes! You can pause or stop a campaign from the floating overlay or Campaign Status screen. To resume, go to Home → Campaign Status → Resume — it will continue from where it stopped."
        ),
        FaqItem(
            "Where can I check campaign reports?",
            "Go to Campaign Status to view all running, paused, or completed campaigns. Use Message Reports or Chat Reports to see delivery details and message history."
        )
    )
}
