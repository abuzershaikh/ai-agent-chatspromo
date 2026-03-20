package com.message.bulksend.tablesheet.ui.components.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.FieldTypes

private val HEADER_BG = Color(0xFF1976D2)
private val ITEM_HEIGHT = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnManageScreen(
    columns: List<ColumnModel>,
    onDismiss: () -> Unit,
    onAddColumn: () -> Unit,
    onEditColumn: (ColumnModel) -> Unit,
    onDeleteColumn: (Long) -> Unit,
    onReorderColumns: (List<ColumnModel>) -> Unit
) {
    // Handle back button
    BackHandler { onDismiss() }
    
    var reorderedColumns by remember(columns) { mutableStateOf(columns) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { ITEM_HEIGHT.toPx() }
    
    // Full screen surface to prevent touch going through
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Columns", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HEADER_BG,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Button(
                    onClick = onAddColumn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HEADER_BG)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Column", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        if (reorderedColumns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ViewColumn,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No columns yet", color = Color.Gray, fontSize = 16.sp)
                    Text("Tap + to add your first column", color = Color.Gray.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                userScrollEnabled = draggedItemIndex == null
            ) {
                itemsIndexed(
                    items = reorderedColumns,
                    key = { _, col -> col.id }
                ) { index, column ->
                    val isDragging = draggedItemIndex == index
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                    
                    DraggableColumnItem(
                        column = column,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        elevation = elevation,
                        onEdit = { onEditColumn(column) },
                        onDelete = { onDeleteColumn(column.id) },
                        onDragStart = { 
                            draggedItemIndex = index 
                            dragOffsetY = 0f
                        },
                        onDrag = { deltaY ->
                            dragOffsetY += deltaY
                            
                            // Calculate swap
                            val currentIndex = draggedItemIndex ?: return@DraggableColumnItem
                            val targetIndex = when {
                                dragOffsetY > itemHeightPx / 2 && currentIndex < reorderedColumns.size - 1 -> currentIndex + 1
                                dragOffsetY < -itemHeightPx / 2 && currentIndex > 0 -> currentIndex - 1
                                else -> null
                            }
                            
                            targetIndex?.let { newIndex ->
                                val newList = reorderedColumns.toMutableList()
                                val item = newList.removeAt(currentIndex)
                                newList.add(newIndex, item)
                                reorderedColumns = newList
                                draggedItemIndex = newIndex
                                dragOffsetY = 0f
                            }
                        },
                        onDragEnd = {
                            if (draggedItemIndex != null) {
                                val updatedColumns = reorderedColumns.mapIndexed { idx, col ->
                                    col.copy(orderIndex = idx)
                                }
                                onReorderColumns(updatedColumns)
                            }
                            draggedItemIndex = null
                            dragOffsetY = 0f
                        }
                    )
                }
            }
        }
    }
    } // Close Surface
}

@Composable
private fun DraggableColumnItem(
    column: ColumnModel,
    isDragging: Boolean,
    dragOffsetY: Float,
    elevation: androidx.compose.ui.unit.Dp,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val typeConfig = FieldTypes.getConfig(column.type)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .padding(vertical = 4.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .shadow(elevation, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color(0xFFE3F2FD) else Color.White
        ),
        border = BorderStroke(1.dp, if (isDragging) HEADER_BG else Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle - touch anywhere on this to drag
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isDragging) HEADER_BG.copy(alpha = 0.1f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) HEADER_BG else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Column info - clickable for edit
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onEdit() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = column.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = typeConfig.name,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                // Type icon
                Icon(
                    typeConfig.icon,
                    contentDescription = typeConfig.name,
                    tint = HEADER_BG,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Visibility toggle
            IconButton(onClick = { /* Toggle visibility */ }) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = "Toggle visibility",
                    tint = Color.Gray
                )
            }
            
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete column",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}