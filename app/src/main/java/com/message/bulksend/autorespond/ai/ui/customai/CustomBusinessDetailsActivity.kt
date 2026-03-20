package com.message.bulksend.autorespond.ai.ui.customai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager

// ─── Theme ──────────────────────────────────────────────────────────────────
private val BG_DEEP       = Color(0xFF0D1117)
private val BG_CARD       = Color(0xFF161B22)
private val BG_CARD2      = Color(0xFF1C2128)
private val BORDER_STD    = Color(0xFF30363D)
private val ACCENT_BLUE   = Color(0xFF388BFD)
private val ACCENT_TEAL   = Color(0xFF2DD4BF)
private val ACCENT_RED    = Color(0xFFF87171)
private val ACCENT_GREEN  = Color(0xFF2EA043)
private val TEXT_WHITE    = Color(0xFFE6EDF3)
private val TEXT_MUTED    = Color(0xFF8B949E)
private val TEXT_SUB      = Color(0xFF58A6FF)
private val FIELD_BG      = Color(0xFF0D1117)

// ─── Activity ──────────────────────────────────────────────────────────────

class CustomBusinessDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CustomBusinessDetailsScreen(
                onBack = { finish() },
                onSaved = {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            )
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, CustomBusinessDetailsActivity::class.java)
        }
    }
}

// ─── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomBusinessDetailsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    val savedKnowledge = remember {
        CustomBusinessKnowledgeCodec.fromJson(settingsManager.customTemplateBusinessKnowledgeJson)
    }

    var businessName    by remember { mutableStateOf(savedKnowledge.businessName) }
    var userName        by remember { mutableStateOf(savedKnowledge.userName) }
    var businessDetails by remember { mutableStateOf(savedKnowledge.businessDetails) }
    val faqItems = remember {
        mutableStateListOf<CustomBusinessFaqItem>().apply {
            addAll(savedKnowledge.faqs)
            if (isEmpty()) add(CustomBusinessFaqItem())
        }
    }

    fun saveKnowledge() {
        val filteredFaqs = faqItems.map {
            CustomBusinessFaqItem(question = it.question.trim(), answer = it.answer.trim())
        }.filter { it.question.isNotBlank() || it.answer.isNotBlank() }

        settingsManager.customTemplateBusinessKnowledgeJson = CustomBusinessKnowledgeCodec.toJson(
            CustomBusinessKnowledge(
                businessName = businessName,
                userName = userName,
                businessDetails = businessDetails,
                faqs = filteredFaqs
            )
        )
        onSaved()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG_DEEP)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Hero Header ───────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0C1B35), BG_DEEP),
                                startY = 0f, endY = 600f
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        // Top row — back + save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF21262D))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TEXT_WHITE,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // Save chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF0F2D11))
                                    .border(1.dp, ACCENT_GREEN, RoundedCornerShape(20.dp))
                                    .padding(0.dp)
                            ) {
                                TextButton(
                                    onClick = { saveKnowledge() },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Save, null, tint = ACCENT_GREEN, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Save", color = ACCENT_GREEN, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        // Icon + Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF1D3461), Color(0xFF0C1B35))))
                                    .border(1.dp, ACCENT_BLUE.copy(0.5f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Business, null, tint = ACCENT_BLUE, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Business Knowledge",
                                    color = TEXT_WHITE,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    "Teach your AI about your business",
                                    color = TEXT_MUTED,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            // ── Info Banner ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111827))
                        .border(1.dp, ACCENT_BLUE.copy(0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = TEXT_SUB, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Fill in your business info — AI will use this to answer customer questions intelligently.",
                        color = TEXT_MUTED,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // ── Business Info Card ────────────────────────────────────────
            item {
                DarkCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionHeader(icon = Icons.Default.Store, title = "Business Info", color = ACCENT_BLUE)
                    Spacer(Modifier.height(12.dp))
                    DarkField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = "Business Name",
                        placeholder = "e.g. Aroma Coffee House",
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    DarkField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = "Your Name (Agent handles as you)",
                        placeholder = "e.g. Ahmed",
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    DarkField(
                        value = businessDetails,
                        onValueChange = { businessDetails = it },
                        label = "Business Details",
                        placeholder = "Describe your products, services, location, timings, policies…",
                        minLines = 6
                    )
                }
            }

            // ── FAQ Header Card ───────────────────────────────────────────
            item {
                DarkCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            SectionHeader(icon = Icons.Default.QuestionAnswer, title = "FAQ Cards", color = ACCENT_TEAL)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F2D2A))
                                .border(1.dp, ACCENT_TEAL.copy(0.5f), RoundedCornerShape(10.dp))
                        ) {
                            TextButton(
                                onClick = { faqItems.add(CustomBusinessFaqItem()) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = ACCENT_TEAL, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add FAQ", color = ACCENT_TEAL, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI will use these Q&A pairs when a customer asks related questions.",
                        color = TEXT_MUTED,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // ── FAQ Items ─────────────────────────────────────────────────
            itemsIndexed(faqItems) { index, faq ->
                DarkCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    bgColor = BG_CARD2
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1D2D40))
                                    .border(1.dp, ACCENT_BLUE.copy(0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = ACCENT_BLUE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("FAQ ${index + 1}", color = TEXT_WHITE, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        if (faqItems.size > 1) {
                            IconButton(
                                onClick = { faqItems.removeAt(index) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A1515))
                            ) {
                                Icon(Icons.Default.Delete, null, tint = ACCENT_RED, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    DarkField(
                        value = faq.question,
                        onValueChange = { faqItems[index] = faq.copy(question = it) },
                        label = "Question",
                        placeholder = "e.g. What are your working hours?",
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    DarkField(
                        value = faq.answer,
                        onValueChange = { faqItems[index] = faq.copy(answer = it) },
                        label = "Answer",
                        placeholder = "e.g. We're open Monday–Saturday, 9am to 9pm.",
                        minLines = 3
                    )
                }
            }

            // ── Save Button ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { saveKnowledge() },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT_BLUE),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Business Knowledge", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

// ─── Reusable components ─────────────────────────────────────────────────────

@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    bgColor: Color = BG_CARD,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, BORDER_STD, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, color = TEXT_WHITE, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TEXT_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF444C56), fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = FIELD_BG,
                unfocusedContainerColor = FIELD_BG,
                focusedTextColor        = TEXT_WHITE,
                unfocusedTextColor      = Color(0xFFCDD9E5),
                cursorColor             = ACCENT_BLUE,
                focusedBorderColor      = ACCENT_BLUE,
                unfocusedBorderColor    = BORDER_STD,
                focusedLabelColor       = ACCENT_BLUE,
                unfocusedLabelColor     = TEXT_MUTED
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}
