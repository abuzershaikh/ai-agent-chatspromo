package com.message.bulksend.templates

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat


import kotlinx.coroutines.delay

// Enhanced Create Template Activity
class CreateTemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val templateId = intent.getStringExtra("TEMPLATE_ID")
        setContent {
            EnhancedTemplatesTheme {
                CreateTemplateScreen(templateId = templateId)
            }
        }
    }
}

@Composable
fun EnhancedTemplatesTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF6C63FF), // Modern purple
        secondary = Color(0xFFFF6B9D), // Pink accent
        tertiary = Color(0xFF4ECDC4), // Teal
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFF8FAFF), // Very light blue-white
        onSurface = Color(0xFF1A1A2E),
        onBackground = Color(0xFF16213E),
        outline = Color(0xFFE8E8E8)
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Safe casting
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTemplateScreen(templateId: String?) {
    val context = LocalContext.current
    val activity = (LocalContext.current as? Activity)
    val templateRepository = remember { TemplateRepository(context) }

    var templateName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var toolInputText by remember { mutableStateOf("") }
    var selectedFancyFont by remember { mutableStateOf("Script") }

    var isVisible by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        if (templateId != null) {
            val templateToEdit = templateRepository.getTemplateById(templateId)
            if (templateToEdit != null) {
                templateName = templateToEdit.name
                message = templateToEdit.message
                mediaUri = templateToEdit.mediaUri?.let { Uri.parse(it) }
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    mediaUri = uri
                } catch (e: SecurityException) {
                    Toast.makeText(context, "Failed to get permission for this file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(800)),
            exit = fadeOut(animationSpec = tween(800))
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                if (templateId == null) "✨ Create Template" else "✏️ Edit Template",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { activity?.finish() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    "Back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            AnimatedSaveButton(
                                enabled = templateName.isNotBlank(),
                                onClick = {
                                    if (templateId == null) {
                                        templateRepository.saveTemplate(
                                            name = templateName,
                                            message = message,
                                            mediaUri = mediaUri?.toString()
                                        )
                                    } else {
                                        val templateToUpdate = templateRepository.getTemplateById(templateId)
                                        if (templateToUpdate != null) {
                                            val updatedTemplate = templateToUpdate.copy(
                                                name = templateName,
                                                message = message,
                                                mediaUri = mediaUri?.toString(),
                                                timestamp = System.currentTimeMillis()
                                            )
                                            templateRepository.updateTemplate(updatedTemplate)
                                        }
                                    }
                                    showSuccessAnimation = true
                                }
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        EnhancedInputField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = "Template Name",
                            icon = Icons.Default.Title
                        )
                    }

                    item {
                        MessageComposerWithTools(
                            value = message,
                            onValueChange = { message = it },
                            activeTool = activeTool,
                            onActiveToolChange = {
                                activeTool = if (activeTool == it) null else it
                            },
                            toolInputText = toolInputText,
                            onToolInputChange = { toolInputText = it },
                            selectedFancyFont = selectedFancyFont,
                            onFancyFontChange = { selectedFancyFont = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        MediaAttachmentCard(
                            mediaUri = mediaUri,
                            onAttachMedia = { mediaPicker.launch(arrayOf("*/*")) },
                            onRemoveMedia = { mediaUri = null }
                        )
                    }

                    if (templateName.isNotBlank() || message.isNotBlank() || mediaUri != null) {
                        item {
                            TemplatePreviewCard(
                                name = templateName,
                                message = message,
                                hasMedia = mediaUri != null
                            )
                        }
                    }
                }
            }
        }

        if (showSuccessAnimation) {
            SuccessAnimationOverlay {
                showSuccessAnimation = false
                activity?.setResult(Activity.RESULT_OK)
                activity?.finish()
            }
        }
    }
}

@Composable
fun EnhancedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
fun MessageComposerWithTools(
    value: String,
    onValueChange: (String) -> Unit,
    activeTool: String?,
    onActiveToolChange: (String) -> Unit,
    toolInputText: String,
    onToolInputChange: (String) -> Unit,
    selectedFancyFont: String,
    onFancyFontChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFontDropdownExpanded by remember { mutableStateOf(false) }
    val fancyFonts = listOf("Script", "Bold Fraktur", "Monospace", "Small Caps", "Cursive")

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = {
                    Text(
                        "Write your message here... 💬",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // Tools Section
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tools:", fontWeight = FontWeight.SemiBold)
                    IconToggleButton(checked = activeTool == "bold", onCheckedChange = { onActiveToolChange("bold") }) {
                        Icon(Icons.Default.FormatBold, "Bold")
                    }
                    IconToggleButton(checked = activeTool == "italic", onCheckedChange = { onActiveToolChange("italic") }) {
                        Icon(Icons.Default.FormatItalic, "Italic")
                    }
                    IconToggleButton(checked = activeTool == "strikethrough", onCheckedChange = { onActiveToolChange("strikethrough") }) {
                        Icon(Icons.Default.FormatStrikethrough, "Strikethrough")
                    }
                    // Fancy Font Dropdown
                    Box {
                        IconToggleButton(checked = activeTool == "fancy", onCheckedChange = {
                            isFontDropdownExpanded = true
                            onActiveToolChange("fancy")
                        }) {
                            Icon(Icons.Default.TextFields, "Fancy Font")
                        }
                        DropdownMenu(
                            expanded = isFontDropdownExpanded,
                            onDismissRequest = { isFontDropdownExpanded = false }
                        ) {
                            fancyFonts.forEach { font ->
                                DropdownMenuItem(
                                    text = { Text(font) },
                                    onClick = {
                                        onFancyFontChange(font)
                                        isFontDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Tool Input Box
                AnimatedVisibility(visible = activeTool != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = toolInputText,
                            onValueChange = onToolInputChange,
                            modifier = Modifier.weight(1f),
                            label = { Text(if (activeTool == "fancy") "Text in $selectedFancyFont" else "Text to be ${activeTool ?: ""}") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val formattedText = when (activeTool) {
                                    "bold" -> "*$toolInputText*"
                                    "italic" -> "_${toolInputText}_"
                                    "strikethrough" -> "~$toolInputText~"
                                    "fancy" -> applyFancyFont(toolInputText, selectedFancyFont)
                                    else -> toolInputText
                                }
                                onValueChange(value + formattedText)
                                onToolInputChange("")
                                onActiveToolChange("") // Deactivates the tool
                            },
                            enabled = toolInputText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, "Apply")
                        }
                    }
                }
            }
        }
    }
}

fun applyFancyFont(text: String, fontStyle: String): String {
    val fontMap: Map<Char, String> = when(fontStyle) {
        "Script" -> scriptMap
        "Bold Fraktur" -> boldFrakturMap
        "Monospace" -> monospaceMap
        "Small Caps" -> smallCapsMap
        "Cursive" -> cursiveMap
        else -> return text
    }
    return text.map { fontMap[it] ?: it.toString() }.joinToString("")
}

@Composable
fun MediaAttachmentCard(
    mediaUri: Uri?,
    onAttachMedia: () -> Unit,
    onRemoveMedia: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (mediaUri != null) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Attachment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        mediaUri.lastPathSegment ?: "Attached File",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onRemoveMedia) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                onClick = onAttachMedia,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (mediaUri == null) "📎 Attach Media" else "🔄 Change Media")
            }
        }
    }
}

@Composable
fun TemplatePreviewCard(
    name: String,
    message: String,
    hasMedia: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("✨ Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            Text(name, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if(message.isNotBlank()) {
                // Use the new FormattedText composable here
                FormattedText(text = message, style = MaterialTheme.typography.bodyMedium)
            }
            if (hasMedia) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Attachment, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Media attached", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

/**
 * A new composable that parses markdown-like formatting and displays it.
 */
@Composable
fun FormattedText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val annotatedString = buildAnnotatedString {
        val regex = Regex("(?<bold>\\*.*?\\*)|(?<italic>_.*?_)|(?<strike>~.*?~)")
        var lastIndex = 0

        withStyle(style = style.toSpanStyle()) {
            regex.findAll(text).forEach { matchResult ->
                val startIndex = matchResult.range.first
                if (startIndex > lastIndex) {
                    append(text.substring(lastIndex, startIndex))
                }

                when {
                    matchResult.groups["bold"] != null -> {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(matchResult.value.removeSurrounding("*"))
                        }
                    }
                    matchResult.groups["italic"] != null -> {
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(matchResult.value.removeSurrounding("_"))
                        }
                    }
                    matchResult.groups["strike"] != null -> {
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(matchResult.value.removeSurrounding("~"))
                        }
                    }
                }
                lastIndex = matchResult.range.last + 1
            }

            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
    Text(annotatedString, modifier = modifier)
}


@Composable
fun AnimatedSaveButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.8f,
        animationSpec = spring(), label = ""
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.scale(scale),
        shape = CircleShape,
        contentPadding = PaddingValues(16.dp)
    ) {
        Icon(Icons.Default.Save, contentDescription = "Save")
    }
}

@Composable
fun SuccessAnimationOverlay(
    onAnimationEnd: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        delay(1500)
        isVisible = false
        delay(300)
        onAnimationEnd()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            val scale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ), label = ""
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = Color.White,
                modifier = Modifier.size(120.dp).scale(scale)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateTemplateScreenPreview() {
    EnhancedTemplatesTheme {
        CreateTemplateScreen(templateId = null)
    }
}

// **FIXED**: Using String literals instead of Char literals for special characters.
private val scriptMap: Map<Char, String> = mapOf(
    'A' to "𝒜", 'B' to "ℬ", 'C' to "𝒞", 'D' to "𝒟", 'E' to "ℰ", 'F' to "ℱ", 'G' to "𝒢", 'H' to "ℋ", 'I' to "ℐ", 'J' to "𝒥", 'K' to "𝒦", 'L' to "ℒ", 'M' to "ℳ", 'N' to "𝒩", 'O' to "𝒪", 'P' to "𝒫", 'Q' to "𝒬", 'R' to "ℛ", 'S' to "𝒮", 'T' to "𝒯", 'U' to "𝒰", 'V' to "𝒱", 'W' to "𝒲", 'X' to "𝒳", 'Y' to "𝒴", 'Z' to "𝒵",
    'a' to "𝒶", 'b' to "𝒷", 'c' to "𝒸", 'd' to "𝒹", 'e' to "ℯ", 'f' to "𝒻", 'g' to "ℊ", 'h' to "𝒽", 'i' to "𝒾", 'j' to "𝒿", 'k' to "𝓀", 'l' to "𝓁", 'm' to "𝓂", 'n' to "𝓃", 'o' to "ℴ", 'p' to "𝓅", 'q' to "𝓆", 'r' to "𝓇", 's' to "𝓈", 't' to "𝓉", 'u' to "𝓊", 'v' to "𝓋", 'w' to "𝓌", 'x' to "𝓍", 'y' to "𝓎", 'z' to "𝓏"
)
private val boldFrakturMap: Map<Char, String> = mapOf(
    'A' to "𝕬", 'B' to "𝕭", 'C' to "𝕮", 'D' to "𝕯", 'E' to "𝕰", 'F' to "𝕱", 'G' to "𝕲", 'H' to "𝕳", 'I' to "𝕴", 'J' to "𝕵", 'K' to "𝕶", 'L' to "𝕷", 'M' to "𝕸", 'N' to "𝕹", 'O' to "𝕺", 'P' to "𝕻", 'Q' to "𝕼", 'R' to "𝕽", 'S' to "𝕾", 'T' to "𝕿", 'U' to "𝖀", 'V' to "𝖁", 'W' to "𝖂", 'X' to "𝖃", 'Y' to "𝖄", 'Z' to "𝖅",
    'a' to "𝖆", 'b' to "𝖇", 'c' to "𝖈", 'd' to "𝖉", 'e' to "𝖊", 'f' to "𝖋", 'g' to "𝖌", 'h' to "𝖍", 'i' to "𝖎", 'j' to "𝖏", 'k' to "𝖐", 'l' to "𝖑", 'm' to "𝖒", 'n' to "𝖓", 'o' to "𝖔", 'p' to "𝖕", 'q' to "𝖖", 'r' to "𝖗", 's' to "𝖘", 't' to "𝖙", 'u' to "𝖚", 'v' to "𝖛", 'w' to "𝖜", 'x' to "𝖝", 'y' to "𝖞", 'z' to "𝖟"
)
private val monospaceMap: Map<Char, String> = mapOf(
    'A' to "𝙰", 'B' to "𝙱", 'C' to "𝙲", 'D' to "𝙳", 'E' to "𝙴", 'F' to "𝙵", 'G' to "𝙶", 'H' to "𝙷", 'I' to "𝙸", 'J' to "𝙹", 'K' to "𝙺", 'L' to "𝙻", 'M' to "𝙼", 'N' to "𝙽", 'O' to "𝙾", 'P' to "𝙿", 'Q' to "𝚀", 'R' to "𝚁", 'S' to "𝚂", 'T' to "𝚃", 'U' to "𝚄", 'V' to "𝚅", 'W' to "𝚆", 'X' to "𝚇", 'Y' to "𝚈", 'Z' to "𝚉",
    'a' to "𝚊", 'b' to "𝚋", 'c' to "𝚌", 'd' to "𝚍", 'e' to "𝚎", 'f' to "𝚏", 'g' to "𝚐", 'h' to "𝚑", 'i' to "𝚒", 'j' to "𝚓", 'k' to "𝚔", 'l' to "𝚕", 'm' to "𝚖", 'n' to "𝚗", 'o' to "𝚘", 'p' to "𝚙", 'q' to "𝚚", 'r' to "𝚛", 's' to "𝚜", 't' to "𝚝", 'u' to "𝚞", 'v' to "𝚟", 'w' to "𝚠", 'x' to "𝚡", 'y' to "𝚢", 'z' to "𝚣"
)
private val smallCapsMap: Map<Char, String> = mapOf(
    'A' to "ᴀ", 'B' to "ʙ", 'C' to "ᴄ", 'D' to "ᴅ", 'E' to "ᴇ", 'F' to "ꜰ", 'G' to "ɢ", 'H' to "ʜ", 'I' to "ɪ", 'J' to "ᴊ", 'K' to "ᴋ", 'L' to "ʟ", 'M' to "ᴍ", 'N' to "ɴ", 'O' to "ᴏ", 'P' to "ᴘ", 'Q' to "ǫ", 'R' to "ʀ", 'S' to "ꜱ", 'T' to "ᴛ", 'U' to "ᴜ", 'V' to "ᴠ", 'W' to "ᴡ", 'X' to "x", 'Y' to "ʏ", 'Z' to "ᴢ"
)
private val cursiveMap: Map<Char, String> = mapOf(
    'A' to "𝓐", 'B' to "𝓑", 'C' to "𝓒", 'D' to "𝓓", 'E' to "𝓔", 'F' to "𝓕", 'G' to "𝓖", 'H' to "𝓗", 'I' to "𝓘", 'J' to "𝓙", 'K' to "𝓚", 'L' to "𝓛", 'M' to "𝓜", 'N' to "𝓝", 'O' to "𝓞", 'P' to "𝓟", 'Q' to "𝓠", 'R' to "𝓡", 'S' to "𝓢", 'T' to "𝓣", 'U' to "𝓤", 'V' to "𝓥", 'W' to "𝓦", 'X' to "𝓧", 'Y' to "𝓨", 'Z' to "𝓩",
    'a' to "𝓪", 'b' to "𝓫", 'c' to "𝓬", 'd' to "𝓭", 'e' to "𝓮", 'f' to "𝓯", 'g' to "𝓰", 'h' to "𝓱", 'i' to "𝓲", 'j' to "𝓳", 'k' to "𝓴", 'l' to "𝓵", 'm' to "𝓶", 'n' to "𝓷", 'o' to "𝓸", 'p' to "𝓹", 'q' to "𝓺", 'r' to "𝓻", 's' to "𝓼", 't' to "𝓽", 'u' to "𝓾", 'v' to "𝓿", 'w' to "𝔀", 'x' to "𝔁", 'y' to "𝔂", 'z' to "𝔃"
)

