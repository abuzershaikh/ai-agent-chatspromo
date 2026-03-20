package com.message.bulksend.templates
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.message.bulksend.R
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.math.abs

class TemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isForSelection = intent.getBooleanExtra("IS_FOR_SELECTION", false)
        setContent {
            ModernTemplatesTheme {
                ManageTemplatesScreen(isForSelection = isForSelection)
            }
        }
    }
}

@Composable
fun ModernTemplatesTheme(content: @Composable () -> Unit) {
    // Modern dark theme with vibrant colors
    val colors = darkColorScheme(
        primary = Color(0xFF7C4DFF), // Vibrant Purple
        primaryContainer = Color(0xFF9E7CFF), // Lighter Purple
        secondary = Color(0xFF00E676), // Vibrant Green
        secondaryContainer = Color(0xFF69F0AE), // Lighter Green
        tertiary = Color(0xFF00B0FF), // Vibrant Blue
        tertiaryContainer = Color(0xFF40C4FF), // Lighter Blue
        surface = Color(0xFF1E1E2E), // Dark Navy
        surfaceVariant = Color(0xFF28293D), // Slightly Lighter Navy
        background = Color(0xFF121212), // Very Dark Blue-Black
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onTertiary = Color.White,
        onSurface = Color(0xFFE0E0FF), // Light Blue-White
        onBackground = Color(0xFFE0E0FF), // Light Blue-White
        outline = Color(0xFF5C6BC0), // Indigo
        outlineVariant = Color(0xFF7986CB), // Light Indigo
        error = Color(0xFFFF5252), // Vibrant Red
        errorContainer = Color(0xFFFF8A80), // Light Red
        onError = Color.White,
        onErrorContainer = Color(0xFF410002),
        scrim = Color(0xFF000000) // Black
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ManageTemplatesScreen(isForSelection: Boolean) {
    val activity = (LocalView.current.context as? Activity)
    val context = LocalContext.current
    val templateRepository = remember { TemplateRepository(context) }
    var myTemplates by remember { mutableStateOf(templateRepository.loadTemplates()) }
    var selectedTemplateIds by remember { mutableStateOf(emptySet<String>()) }
    var sortOrder by remember { mutableStateOf("Latest") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val templateActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            myTemplates = templateRepository.loadTemplates()
        }
    }

    val createTemplateIntent = Intent(context, CreateTemplateActivity::class.java)

    // Create gradient for top bar
    val topBarGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            if (isForSelection) "Select a Template" else "My Templates",
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isForSelection) {
                            Text(
                                "Create and manage your templates",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { activity?.finish() },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (!isForSelection) {
                        AnimatedVisibility(
                            visible = selectedTemplateIds.isNotEmpty(),
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("${selectedTemplateIds.size}")
                                }
                                IconButton(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = selectedTemplateIds.isEmpty(),
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            EnhancedSortMenu(sortOrder = sortOrder, onSortOrderChange = { sortOrder = it })
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!isForSelection) {
                ExtendedFloatingActionButton(
                    onClick = { templateActivityLauncher.launch(createTemplateIntent) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Template")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Template", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val sortedTemplates = when (sortOrder) {
            "Latest" -> myTemplates.sortedByDescending { it.timestamp }
            "Oldest" -> myTemplates.sortedBy { it.timestamp }
            "A-Z" -> myTemplates.sortedBy { it.name }
            "Z-A" -> myTemplates.sortedByDescending { it.name }
            else -> myTemplates
        }

        EnhancedMyTemplatesScreen(
            templates = sortedTemplates,
            selectedIds = selectedTemplateIds,
            onTemplateClick = { template ->
                if (isForSelection) {
                    val resultIntent = Intent().apply {
                        putExtra("SELECTED_TEMPLATE_ID", template.id)
                    }
                    activity?.setResult(Activity.RESULT_OK, resultIntent)
                    activity?.finish()
                } else {
                    val id = template.id
                    selectedTemplateIds = if (selectedTemplateIds.contains(id)) {
                        selectedTemplateIds - id
                    } else {
                        selectedTemplateIds + id
                    }
                }
            },
            onTemplateLongClick = { template ->
                if (!isForSelection) {
                    selectedTemplateIds = selectedTemplateIds + template.id
                }
            },
            onEditClick = { templateId ->
                val intent = Intent(context, CreateTemplateActivity::class.java).apply {
                    putExtra("TEMPLATE_ID", templateId)
                }
                templateActivityLauncher.launch(intent)
            },
            onCreateClick = {
                templateActivityLauncher.launch(createTemplateIntent)
            },
            isForSelection = isForSelection,
            modifier = Modifier.padding(innerPadding)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "Delete Templates",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedTemplateIds.size} template(s)? This action cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        templateRepository.deleteTemplates(selectedTemplateIds)
                        myTemplates = templateRepository.loadTemplates()
                        selectedTemplateIds = emptySet()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun EnhancedSortMenu(sortOrder: String, onSortOrderChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            listOf("Latest", "Oldest", "A-Z", "Z-A").forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSortOrderChange(option)
                        expanded = false
                    },
                    leadingIcon = {
                        if (sortOrder == option) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EnhancedMyTemplatesScreen(
    templates: List<Template>,
    selectedIds: Set<String>,
    onTemplateClick: (Template) -> Unit,
    onTemplateLongClick: (Template) -> Unit,
    onEditClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    isForSelection: Boolean,
    modifier: Modifier = Modifier
) {
    if (templates.isEmpty()) {
        EmptyStateView(onCreateClick = onCreateClick, modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                EnhancedUserTemplateCard(
                    template = template,
                    isSelected = template.id in selectedIds,
                    onClick = { onTemplateClick(template) },
                    onLongClick = { onTemplateLongClick(template) },
                    onEditClick = { onEditClick(template.id) },
                    isForSelection = isForSelection
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Create a gradient background for the icon
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            ),
                            center = Offset(90f, 90f),
                            radius = 180f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "No Templates Yet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Create your first template to get started with quick messaging",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedUserTemplateCard(
    template: Template,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit,
    isForSelection: Boolean
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(300), label = ""
    )

    val animatedColor by animateColorAsState(
        targetValue = if (isSelected && !isForSelection)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(300), label = ""
    )

    // Define vibrant gradients for the color bar
    val gradients = listOf(
        listOf(Color(0xFF7C4DFF), Color(0xFF9E7CFF)), // Purple
        listOf(Color(0xFF00E676), Color(0xFF69F0AE)), // Green
        listOf(Color(0xFF00B0FF), Color(0xFF40C4FF)), // Blue
        listOf(Color(0xFFFF9800), Color(0xFFFFB74D)), // Orange
        listOf(Color(0xFFE91E63), Color(0xFFF06292))  // Pink
    )

    val selectedGradient = remember { gradients[abs(template.id.hashCode()) % gradients.size] }

    // Create a gradient for the card background
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        colors = CardDefaults.cardColors(containerColor = animatedColor),
        border = if (isSelected && !isForSelection)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.background(cardGradient),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colorful Bar with gradient
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(80.dp)
                    .background(Brush.verticalGradient(selectedGradient))
            )

            Row(
                modifier = Modifier
                    .padding(start = if (isForSelection) 16.dp else 0.dp, end = 8.dp)
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isForSelection) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = template.message.ifBlank { "No message content" },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }

            if (template.mediaUri != null) {
                Icon(
                    Icons.Default.Attachment,
                    contentDescription = "Has Media",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            if (!isForSelection) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Select Template",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}