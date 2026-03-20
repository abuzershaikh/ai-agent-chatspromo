package com.message.bulksend.autorespond.ai.customtask.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.message.bulksend.autorespond.ai.customtask.manager.AgentTaskManager
import com.message.bulksend.autorespond.ai.customtask.models.AgentTask
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.ai.ui.customai.CustomBusinessDetailsActivity
import com.message.bulksend.autorespond.ai.ui.customai.CustomBusinessKnowledgeCodec

// ─── Colors ────────────────────────────────────────────────────────────
private val BG_DEEP    = Color(0xFF0B0F17)
private val CARD_BG    = Color(0xFF141921)
private val CARD_BG2   = Color(0xFF1A2030)
private val BORDER_COL = Color(0xFF252D3D)
private val TEXT_W     = Color(0xFFE6EDF3)
private val TEXT_MUT   = Color(0xFF8B949E)
private val BLUE       = Color(0xFF388BFD)
private val TEAL       = Color(0xFF2DD4BF)
private val RED        = Color(0xFFF87171)

private val STEP_COLORS = listOf(
    Color(0xFF3B82F6), Color(0xFFFF6B35), Color(0xFF10B981),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF06B6D4),
    Color(0xFFF59E0B), Color(0xFFEF4444)
)

class AgentTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { AgentTaskScreen(onBack = { finish() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentTaskScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AIAgentSettingsManager(context) }
    val taskManager = remember { AgentTaskManager(context) }

    var taskModeEnabled by rememberSaveable { mutableStateOf(settings.customTemplateTaskModeEnabled) }
    var tasks by remember { mutableStateOf(taskManager.getTasks()) }
    var showDeleteFlowDialog by remember { mutableStateOf(false) }
    var businessKnowledge by remember {
        mutableStateOf(CustomBusinessKnowledgeCodec.fromJson(settings.customTemplateBusinessKnowledgeJson))
    }

    fun refreshTasks() { tasks = taskManager.getTasks() }
    fun refreshBusinessKnowledge() {
        businessKnowledge = CustomBusinessKnowledgeCodec.fromJson(settings.customTemplateBusinessKnowledgeJson)
    }

    val stepEditorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val stepResult = AgentTaskEditorActivity.extractResult(result.data) ?: return@rememberLauncherForActivityResult
        taskManager.addTask(
            title = stepResult.title,
            goal = stepResult.goal,
            instruction = stepResult.instruction,
            followUpQuestion = stepResult.question,
            allowedTools = stepResult.allowedTools,
            agentFormTemplateKey = stepResult.agentFormTemplateKey
        )
        refreshTasks()
        Toast.makeText(context, "Step added", Toast.LENGTH_SHORT).show()
    }

    val businessDetailsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        refreshBusinessKnowledge()
        Toast.makeText(context, "Business knowledge saved", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TaskFlowDottedBackground(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top bar ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117).copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF21262D))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TEXT_W, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Step Flow Tasks", color = TEXT_W, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Smart AI agent step flow", color = TEXT_MUT, fontSize = 11.sp)
                    }
                    // Add Step — compact, not full-width
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BLUE.copy(0.15f))
                            .border(1.dp, BLUE.copy(0.5f), RoundedCornerShape(10.dp))
                    ) {
                        TextButton(
                            onClick = { stepEditorLauncher.launch(AgentTaskEditorActivity.createAddIntent(context)) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = BLUE, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Step", color = BLUE, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Control card ──────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CARD_BG)
                            .border(1.dp, BORDER_COL, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Task Mode toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Task Mode", color = TEXT_W, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Custom template follows ordered steps when enabled.",
                                        color = TEXT_MUT, fontSize = 11.sp, lineHeight = 16.sp
                                    )
                                }
                                Switch(
                                    checked = taskModeEnabled,
                                    onCheckedChange = { enabled ->
                                        taskModeEnabled = enabled
                                        settings.customTemplateTaskModeEnabled = enabled
                                        if (enabled) settings.customTemplatePromptMode = "STEP_FLOW"
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BLUE
                                    )
                                )
                            }

                            HorizontalDivider(color = BORDER_COL, thickness = 0.5.dp)

                            // Business knowledge status
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D1117))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Business, null, tint = TEAL, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (businessKnowledge.hasContent()) {
                                        buildString {
                                            append("Knowledge ready")
                                            if (businessKnowledge.businessName.isNotBlank()) append(" · ${businessKnowledge.businessName}")
                                            if (businessKnowledge.faqs.isNotEmpty()) append(" · ${businessKnowledge.faqs.size} FAQs")
                                        }
                                    } else "No business knowledge set yet. Add info so AI can answer side questions." ,
                                    color = TEXT_MUT, fontSize = 11.sp, lineHeight = 15.sp
                                )
                            }

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Business Info button (compact)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(TEAL.copy(0.1f))
                                        .border(1.dp, TEAL.copy(0.4f), RoundedCornerShape(9.dp))
                                ) {
                                    TextButton(
                                        onClick = { businessDetailsLauncher.launch(CustomBusinessDetailsActivity.createIntent(context)) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, null, tint = TEAL, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Business Info", color = TEAL, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                // Delete Flow (subtle)
                                TextButton(
                                    onClick = { showDeleteFlowDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, null, tint = RED.copy(0.8f), modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete Flow", color = RED.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // ── Empty state ───────────────────────────────────────────
                if (tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CARD_BG)
                                .border(1.dp, BORDER_COL, RoundedCornerShape(14.dp))
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AccountTree, null, tint = TEXT_MUT, modifier = Modifier.size(36.dp))
                                Text("No steps yet", color = TEXT_W, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Tap \"Add Step\" in the top-right to create your first step.", color = TEXT_MUT, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // ── Step cards with animated dot connectors ───────────
                    itemsIndexed(tasks, key = { _, task -> task.taskId }) { index, task ->
                        TaskItemCard(
                            task = task,
                            onDelete = {
                                taskManager.deleteTask(task.taskId)
                                refreshTasks()
                            }
                        )
                        // Animated dot connector between cards (not after last)
                        if (index < tasks.lastIndex) {
                            AnimatedStepConnector(
                                fromColor = STEP_COLORS[task.stepOrder % STEP_COLORS.size],
                                toColor = STEP_COLORS[(task.stepOrder + 1) % STEP_COLORS.size]
                            )
                        }
                    }
                }

                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }

    // Delete flow dialog
    if (showDeleteFlowDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFlowDialog = false },
            containerColor = CARD_BG2,
            titleContentColor = TEXT_W,
            textContentColor = TEXT_MUT,
            title = { Text("Delete entire flow?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = { Text("This will delete all step-flow tasks and active task sessions for all users.", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    taskManager.clearTasks()
                    taskManager.clearAllSessions()
                    settings.customTemplateTaskModeEnabled = false
                    settings.customTemplatePromptMode = AIAgentSettingsManager.PROMPT_MODE_SIMPLE
                    taskModeEnabled = false
                    refreshTasks()
                    showDeleteFlowDialog = false
                    Toast.makeText(context, "Step flow deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = RED, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFlowDialog = false }) {
                    Text("Cancel", color = TEXT_MUT, fontSize = 13.sp)
                }
            }
        )
    }
}

// ─── Step Item Card ─────────────────────────────────────────────────────────

@Composable
private fun TaskItemCard(task: AgentTask, onDelete: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    val toolLabels = AgentTaskToolRegistry.labelsFor(task.allowedTools.orEmpty())
    val accentColor = STEP_COLORS[task.stepOrder % STEP_COLORS.size]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CARD_BG)
            .border(1.dp, BORDER_COL, RoundedCornerShape(14.dp))
            // Colored left accent strip
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(listOf(accentColor, accentColor.copy(0.3f))),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Step badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(accentColor.copy(0.15f))
                            .border(1.dp, accentColor.copy(0.5f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Step ${task.stepOrder}",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        task.title,
                        color = TEXT_W,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Only Delete icon — no copy
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(RED.copy(0.1f))
                ) {
                    Icon(Icons.Default.Delete, null, tint = RED, modifier = Modifier.size(15.dp))
                }
            }

            // Tool chips
            if (toolLabels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    toolLabels.take(3).forEachIndexed { i, label ->
                        val c = STEP_COLORS[i % STEP_COLORS.size]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(c.copy(0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(label, color = c, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (toolLabels.size > 3) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF252D3D))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("+${toolLabels.size - 3}", color = TEXT_MUT, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                Text("No tools selected", color = TEXT_MUT, fontSize = 11.sp)
            }

            // Expand indicator
            Text(
                if (isExpanded) "▲ less" else "▼ details",
                color = accentColor.copy(0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            // Expanded detail section
            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HorizontalDivider(color = BORDER_COL, thickness = 0.5.dp)

                    if (task.goal.isNotBlank()) {
                        DetailRow(label = "Goal", value = task.goal, color = BLUE)
                    }
                    DetailRow(label = "Instruction", value = task.instruction, color = TEXT_MUT)
                    if (task.followUpQuestion.isNotBlank()) {
                        DetailRow(label = "Question", value = task.followUpQuestion, color = TEAL)
                    }
                    if (toolLabels.isNotEmpty()) {
                        DetailRow(label = "All Tools", value = toolLabels.joinToString(", "), color = Color(0xFFF59E0B))
                    }
                    val hasSendAgentForm = AgentTaskToolRegistry.normalizeToolIds(task.allowedTools.orEmpty())
                        .contains(AgentTaskToolRegistry.SEND_AGENT_FORM)
                    if (hasSendAgentForm && task.agentFormTemplateKey.isNotBlank()) {
                        DetailRow(label = "Agent Form", value = task.agentFormTemplateKey, color = Color(0xFF8B5CF6))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(0.07f))
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text("$label: ", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TEXT_W.copy(0.85f), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

// ─── Animated Step Connector (···· staggered dots) ─────────────────────────────

@Composable
private fun AnimatedStepConnector(
    fromColor: Color,
    toColor: Color
) {
    // Number of dots in the column
    val dotCount = 8
    val dotSpacingFraction = 1f / (dotCount + 1)
    val cycleDurationMs = 1600

    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    // Single shared progress 0..1 for the whole animation cycle
    val masterProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleDurationMs, easing = LinearEasing)
        ),
        label = "masterProgress"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val cx = size.width / 2f
        val totalHeight = size.height
        val dotRadius = 3.5.dp.toPx()
        val glowRadius = 7.dp.toPx()

        for (i in 0 until dotCount) {
            // Each dot has its own phase offset — staggered evenly
            val stagger = i.toFloat() / dotCount
            // Brightness cycles: dot is brightest when it's "on" in the wave
            // Wave travels top-to-bottom: each dot's brightness is sin of (masterProgress - stagger)
            val phase = ((masterProgress - stagger + 1f) % 1f)  // 0..1
            // Brightness peaks at phase=0 using a sine-like curve: cos(phase * 2π) mapped 0..1
            val brightness = ((Math.cos(phase * 2.0 * Math.PI).toFloat() + 1f) / 2f)
                .coerceIn(0f, 1f)

            // Y position is fixed — dots are evenly spaced vertically
            val yPos = totalHeight * dotSpacingFraction * (i + 1)

            // Interpolate color from top to bottom
            val lerpT = i.toFloat() / (dotCount - 1).coerceAtLeast(1)
            val dotColor = lerp(fromColor, toColor, lerpT)

            if (brightness > 0.05f) {
                // Soft glow behind dot
                drawCircle(
                    color = dotColor.copy(alpha = brightness * 0.25f),
                    radius = glowRadius,
                    center = Offset(cx, yPos)
                )
                // Core dot
                drawCircle(
                    color = dotColor.copy(alpha = brightness.coerceAtLeast(0.12f)),
                    radius = dotRadius * (0.4f + brightness * 0.6f),
                    center = Offset(cx, yPos)
                )
            } else {
                // Always show a dim ghost dot so the grid is visible
                drawCircle(
                    color = Color(0xFF2F3A4D).copy(alpha = 0.45f),
                    radius = dotRadius * 0.55f,
                    center = Offset(cx, yPos)
                )
            }
        }
    }
}

// lerp helper for Color
private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)

// ─── Dotted background ───────────────────────────────────────────────────────

@Composable
fun TaskFlowDottedBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF0B0F17), Color(0xFF121826)))
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF2563EB).copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.85f),
                radius = size.minDimension * 0.75f
            )
        )
        val spacing = 26f
        val dotColor = Color(0xFF2F3A4D).copy(alpha = 0.7f)
        val columns = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 1
        for (i in 0 until columns) {
            for (j in 0 until rows) {
                val xOffset = if (j % 2 == 0) 0f else spacing / 2f
                drawCircle(color = dotColor, radius = 2f, center = Offset(i * spacing + xOffset, j * spacing))
            }
        }
    }
}
