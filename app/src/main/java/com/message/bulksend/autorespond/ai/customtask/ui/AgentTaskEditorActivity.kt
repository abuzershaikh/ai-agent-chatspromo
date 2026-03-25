package com.message.bulksend.autorespond.ai.customtask.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.ai.customtask.models.AgentTaskToolRegistry
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.ui.theme.BulksendTestTheme

/**
 * Dedicated editor screen for task step create/edit.
 *
 * Keeping this in a separate activity makes future edits easier:
 * - can extend for edit mode
 * - can add flow/question/tool fields without cluttering list screen
 */
class AgentTaskEditorActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MODE = "task_editor_mode"
        private const val EXTRA_TITLE = "task_editor_title"
        private const val EXTRA_GOAL = "task_editor_goal"
        private const val EXTRA_INSTRUCTION = "task_editor_instruction"
        private const val EXTRA_QUESTION = "task_editor_question"
        private const val EXTRA_ALLOWED_TOOLS = "task_editor_allowed_tools"
        private const val EXTRA_AGENT_FORM_TEMPLATE_KEY = "task_editor_agent_form_template_key"

        private const val MODE_ADD = "add"
        private const val MODE_EDIT = "edit"

        fun createAddIntent(context: Context): Intent {
            return Intent(context, AgentTaskEditorActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_ADD)
            }
        }

        // Reserved for future edit support.
        fun createEditIntent(
                context: Context,
                title: String,
                goal: String,
                instruction: String,
                question: String,
                allowedTools: List<String>,
                agentFormTemplateKey: String = ""
        ): Intent {
            return Intent(context, AgentTaskEditorActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_EDIT)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_GOAL, goal)
                putExtra(EXTRA_INSTRUCTION, instruction)
                putExtra(EXTRA_QUESTION, question)
                putStringArrayListExtra(EXTRA_ALLOWED_TOOLS, ArrayList(allowedTools))
                putExtra(EXTRA_AGENT_FORM_TEMPLATE_KEY, agentFormTemplateKey.trim())
            }
        }

        fun extractResult(data: Intent?): TaskEditorResult? {
            if (data == null) return null
            val title = data.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()
            val instruction = data.getStringExtra(EXTRA_INSTRUCTION)?.trim().orEmpty()
            if (title.isBlank() || instruction.isBlank()) return null

            val allowedTools =
                    AgentTaskToolRegistry.normalizeToolIds(
                            data.getStringArrayListExtra(EXTRA_ALLOWED_TOOLS).orEmpty()
                    )

            return TaskEditorResult(
                    title = title,
                    goal = data.getStringExtra(EXTRA_GOAL)?.trim().orEmpty(),
                    instruction = instruction,
                    question = data.getStringExtra(EXTRA_QUESTION)?.trim().orEmpty(),
                    allowedTools = allowedTools,
                    agentFormTemplateKey =
                            data.getStringExtra(EXTRA_AGENT_FORM_TEMPLATE_KEY)?.trim().orEmpty()
            )
        }
    }

    data class TaskEditorResult(
            val title: String,
            val goal: String,
            val instruction: String,
            val question: String,
            val allowedTools: List<String>,
            val agentFormTemplateKey: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ADD
        val initialTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val initialGoal = intent.getStringExtra(EXTRA_GOAL).orEmpty()
        val initialInstruction = intent.getStringExtra(EXTRA_INSTRUCTION).orEmpty()
        val initialQuestion = intent.getStringExtra(EXTRA_QUESTION).orEmpty()
        val initialAgentFormTemplateKey =
                intent.getStringExtra(EXTRA_AGENT_FORM_TEMPLATE_KEY).orEmpty()
        val initialAllowedTools =
                AgentTaskToolRegistry.normalizeToolIds(
                        intent.getStringArrayListExtra(EXTRA_ALLOWED_TOOLS).orEmpty()
                )

        val settings = AIAgentSettingsManager(this)
        val availableTools = AgentTaskToolRegistry.enabledTools(settings)

        setContent {
            BulksendTestTheme {
                TaskEditorScreen(
                        mode = mode,
                        initialTitle = initialTitle,
                        initialGoal = initialGoal,
                        initialInstruction = initialInstruction,
                        initialQuestion = initialQuestion,
                        initialAgentFormTemplateKey = initialAgentFormTemplateKey,
                        initialAllowedTools = initialAllowedTools,
                        availableTools = availableTools,
                        onBack = { finish() },
                        onSave = { result ->
                            setResult(
                                    Activity.RESULT_OK,
                                    Intent().apply {
                                        putExtra(EXTRA_TITLE, result.title)
                                        putExtra(EXTRA_GOAL, result.goal)
                                        putExtra(EXTRA_INSTRUCTION, result.instruction)
                                        putExtra(EXTRA_QUESTION, result.question)
                                        putExtra(
                                                EXTRA_AGENT_FORM_TEMPLATE_KEY,
                                                result.agentFormTemplateKey
                                        )
                                        putStringArrayListExtra(
                                                EXTRA_ALLOWED_TOOLS,
                                                ArrayList(result.allowedTools)
                                        )
                                    }
                            )
                            finish()
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorScreen(
        mode: String,
        initialTitle: String,
        initialGoal: String,
        initialInstruction: String,
        initialQuestion: String,
        initialAgentFormTemplateKey: String,
        initialAllowedTools: List<String>,
        availableTools: List<AgentTaskToolRegistry.ToolDefinition>,
        onBack: () -> Unit,
        onSave: (AgentTaskEditorActivity.TaskEditorResult) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var goal by remember { mutableStateOf(initialGoal) }
    var instruction by remember { mutableStateOf(initialInstruction) }
    var question by remember { mutableStateOf(initialQuestion) }
    var agentFormTemplateKey by remember { mutableStateOf(initialAgentFormTemplateKey.trim()) }

    val availableToolIds = remember(availableTools) { availableTools.map { it.id }.toSet() }
    var selectedToolIds by
            remember(initialAllowedTools, availableToolIds) {
                mutableStateOf(
                        AgentTaskToolRegistry.normalizeToolIds(initialAllowedTools)
                                .filter { it in availableToolIds }
                                .toSet()
                )
            }

    val agentFormTemplates by
            produceState(initialValue = emptyList<AgentFormTemplateOption>()) {
                value = AgentFormTemplatePickerRepository.loadOptions(context)
            }
    val isSendAgentFormSelected = AgentTaskToolRegistry.SEND_AGENT_FORM in selectedToolIds
    val canSave =
            title.trim().isNotBlank() &&
                    instruction.trim().isNotBlank() &&
                    (!isSendAgentFormSelected || agentFormTemplateKey.isNotBlank())

    Box(modifier = Modifier.fillMaxSize()) {
        // N8N-style dotted canvas background
        TaskEditorDottedBackground(modifier = Modifier.matchParentSize())

        Scaffold(
                topBar = {
                    TopAppBar(
                            title = {
                                Text(
                                        text = if (mode == "edit") "Edit Step" else "Add Step",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                TextButton(
                                        enabled = canSave,
                                        onClick = {
                                            onSave(
                                                    AgentTaskEditorActivity.TaskEditorResult(
                                                            title = title.trim(),
                                                            goal = goal.trim(),
                                                            instruction = instruction.trim(),
                                                            question = question.trim(),
                                                            allowedTools =
                                                                    AgentTaskToolRegistry
                                                                            .normalizeToolIds(
                                                                                    selectedToolIds
                                                                            ),
                                                            agentFormTemplateKey =
                                                                    agentFormTemplateKey.trim()
                                                    )
                                            )
                                        }
                                ) { Text("Save", color = if (canSave) Color.White else Color.Gray) }
                            },
                            colors =
                                    TopAppBarDefaults.topAppBarColors(
                                            containerColor = Color(0xFF2563EB)
                                    )
                    )
                },
                containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                "Step Details",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                        )
                        Text(
                                "Configure step instruction, question flow, and final success-check goal.",
                                color = Color(0xFFB4D8F0),
                                fontSize = 13.sp
                        )
                    }
                }

                Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Step title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB),
                                                focusedLabelColor = Color(0xFF2563EB),
                                                unfocusedBorderColor = Color(0xFF4B5563),
                                                unfocusedLabelColor = Color(0xFF9CA3AF),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = Color(0xFF2563EB)
                                        ),
                                shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                                value = instruction,
                                onValueChange = { instruction = it },
                                label = { Text("Step instruction") },
                                minLines = 3,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB),
                                                focusedLabelColor = Color(0xFF2563EB),
                                                unfocusedBorderColor = Color(0xFF4B5563),
                                                unfocusedLabelColor = Color(0xFF9CA3AF),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = Color(0xFF2563EB)
                                        ),
                                shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                                value = question,
                                onValueChange = { question = it },
                                label = { Text("Question to ask user (optional)") },
                                minLines = 2,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB),
                                                focusedLabelColor = Color(0xFF2563EB),
                                                unfocusedBorderColor = Color(0xFF4B5563),
                                                unfocusedLabelColor = Color(0xFF9CA3AF),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = Color(0xFF2563EB)
                                        ),
                                shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Step success check (optional)") },
                                placeholder = { Text("Is step ke complete hone ka clear condition likho") },
                                minLines = 2,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB),
                                                focusedLabelColor = Color(0xFF2563EB),
                                                unfocusedBorderColor = Color(0xFF4B5563),
                                                unfocusedLabelColor = Color(0xFF9CA3AF),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = Color(0xFF2563EB)
                                        ),
                                shape = RoundedCornerShape(12.dp)
                        )

                        if (availableTools.isEmpty()) {
                            Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors =
                                            CardDefaults.cardColors(
                                                    containerColor = Color(0xFF374151)
                                            )
                            ) {
                                Text(
                                        text = "No tools are enabled in Custom Template settings.",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            ToolMultiSelectDropdown(
                                    availableTools = availableTools,
                                    selectedToolIds = selectedToolIds,
                                    onSelectionChange = { selectedToolIds = it }
                            )
                            if (isSendAgentFormSelected) {
                                if (agentFormTemplates.isEmpty()) {
                                    Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors =
                                                    CardDefaults.cardColors(
                                                            containerColor = Color(0xFF374151)
                                                    )
                                    ) {
                                        Text(
                                                text =
                                                        "No form template found. Create one in AgentForm before using Send Agent Form.",
                                                color = Color(0xFFEF4444),
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    AgentFormTemplateDropdown(
                                            templates = agentFormTemplates,
                                            selectedKey = agentFormTemplateKey,
                                            onSelectionChange = { selected ->
                                                agentFormTemplateKey = selected
                                            }
                                    )
                                }
                                Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor = Color(0xFF374151)
                                                )
                                ) {
                                    Text(
                                            text =
                                                    "Send Agent Form needs a selected form template. Agent will send only that template for this step.",
                                            color = Color(0xFF93C5FD),
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors =
                                            CardDefaults.cardColors(
                                                    containerColor = Color(0xFF374151)
                                            )
                            ) {
                                Text(
                                        text =
                                                if (selectedToolIds.isEmpty()) {
                                                    "No step tool selected. Only default tools like Sheet Write can run if enabled."
                                                } else {
                                                    "Only selected tools will be allowed for this step."
                                                },
                                        color = Color(0xFF93C5FD),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                    AgentTaskEditorActivity.TaskEditorResult(
                                            title = title.trim(),
                                            goal = goal.trim(),
                                            instruction = instruction.trim(),
                                            question = question.trim(),
                                            allowedTools =
                                                    AgentTaskToolRegistry.normalizeToolIds(
                                                            selectedToolIds
                                                    ),
                                            agentFormTemplateKey = agentFormTemplateKey.trim()
                                    )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2563EB),
                                        disabledContainerColor = Color(0xFF374151)
                                ),
                        shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                            if (mode == "edit") "Update Step" else "Create Step",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolMultiSelectDropdown(
        availableTools: List<AgentTaskToolRegistry.ToolDefinition>,
        selectedToolIds: Set<String>,
        onSelectionChange: (Set<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedSummary =
            if (selectedToolIds.isEmpty()) {
                "No tool selected"
            } else {
                availableTools.filter { it.id in selectedToolIds }.joinToString(", ") { it.label }
            }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
                value = selectedSummary,
                onValueChange = {},
                readOnly = true,
                label = { Text("Allowed tools") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2563EB),
                                focusedLabelColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFF4B5563),
                                unfocusedLabelColor = Color(0xFF9CA3AF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color.White,
                                disabledBorderColor = Color(0xFF4B5563),
                                disabledLabelColor = Color(0xFF9CA3AF)
                        ),
                shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF0B0F17))
        ) {
            availableTools.forEachIndexed { index, tool ->
                val isSelected = tool.id in selectedToolIds
                // Super bright vibrant colors for each item
                val cardColor =
                        when (index % 6) {
                            0 -> Color(0xFF3B82F6) // Electric Blue
                            1 -> Color(0xFFFF6B35) // Bright Orange
                            2 -> Color(0xFF10B981) // Bright Green
                            3 -> Color(0xFF8B5CF6) // Bright Purple
                            4 -> Color(0xFFEC4899) // Hot Pink
                            else -> Color(0xFF06B6D4) // Bright Cyan
                        }

                Card(
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (isSelected) cardColor
                                                else cardColor.copy(alpha = 0.4f)
                                ),
                        elevation =
                                CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 6.dp else 2.dp
                                ),
                        modifier =
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    DropdownMenuItem(
                            text = {
                                Text(
                                        if (isSelected) "✓ ${tool.label}" else tool.label,
                                        color = Color.White,
                                        fontWeight =
                                                if (isSelected) FontWeight.Bold
                                                else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                )
                            },
                            onClick = {
                                val updated = selectedToolIds.toMutableSet()
                                if (isSelected) {
                                    updated.remove(tool.id)
                                } else {
                                    updated.add(tool.id)
                                }
                                onSelectionChange(updated)
                                // Close dropdown after selection
                                expanded = false
                            },
                            colors = MenuDefaults.itemColors(textColor = Color.White)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentFormTemplateDropdown(
        templates: List<AgentFormTemplateOption>,
        selectedKey: String,
        onSelectionChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTemplate = templates.firstOrNull { it.key.equals(selectedKey, ignoreCase = true) }
    val selectedLabel =
            selectedTemplate?.let { "${it.title} (${it.key})" } ?: "Select form template"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Agent Form template") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2563EB),
                                focusedLabelColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFF4B5563),
                                unfocusedLabelColor = Color(0xFF9CA3AF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color.White,
                                disabledBorderColor = Color(0xFF4B5563),
                                disabledLabelColor = Color(0xFF9CA3AF)
                        ),
                shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF0B0F17))
        ) {
            templates.forEachIndexed { index, template ->
                val typeLabel = if (template.isCustom) "Custom" else "Default"
                val isSelected = template.key.equals(selectedKey, ignoreCase = true)

                // Vibrant gradient colors for each template
                val cardColor =
                        when (index % 6) {
                            0 -> Color(0xFF2563EB) // Bright Blue
                            1 -> Color(0xFFEA580C) // Bright Orange
                            2 -> Color(0xFF059669) // Bright Green
                            3 -> Color(0xFF7C3AED) // Bright Purple
                            4 -> Color(0xFFDB2777) // Bright Pink
                            else -> Color(0xFF0891B2) // Bright Cyan
                        }

                Card(
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (isSelected) cardColor
                                                else cardColor.copy(alpha = 0.3f)
                                ),
                        elevation =
                                CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 4.dp else 2.dp
                                ),
                        modifier =
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                            if (isSelected) "✓ ${template.title}"
                                            else template.title,
                                            color = Color.White,
                                            fontWeight =
                                                    if (isSelected) FontWeight.Bold
                                                    else FontWeight.SemiBold,
                                            fontSize = 14.sp
                                    )
                                    Text(
                                            "$typeLabel • ${template.key}",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            onClick = {
                                onSelectionChange(template.key)
                                expanded = false
                            },
                            colors = MenuDefaults.itemColors(textColor = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskEditorDottedBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // N8N-style dark canvas base with gradient
        drawRect(
                brush =
                        Brush.verticalGradient(
                                colors = listOf(Color(0xFF0B0F17), Color(0xFF121826))
                        )
        )

        // Subtle blue radial gradient accent
        drawRect(
                brush =
                        Brush.radialGradient(
                                colors =
                                        listOf(
                                                Color(0xFF2563EB).copy(alpha = 0.08f),
                                                Color.Transparent
                                        ),
                                center = Offset(size.width * 0.1f, size.height * 0.85f),
                                radius = size.minDimension * 0.75f
                        )
        )

        // Draw dotted pattern
        val dotRadius = 2.0f
        val spacing = 26f
        val dotColor = Color(0xFF2F3A4D).copy(alpha = 0.7f)

        val columns = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 1

        for (i in 0 until columns) {
            for (j in 0 until rows) {
                val xOffset = if (j % 2 == 0) 0f else spacing / 2f
                drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(i * spacing + xOffset, j * spacing)
                )
            }
        }
    }
}
