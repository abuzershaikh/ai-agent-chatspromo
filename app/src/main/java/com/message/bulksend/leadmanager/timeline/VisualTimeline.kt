package com.message.bulksend.leadmanager.timeline

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.message.bulksend.leadmanager.database.entities.TimelineEventType
import com.message.bulksend.leadmanager.model.TimelineEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * Visual Timeline with snake/curved path design
 */
@Composable
fun VisualTimelineSnake(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val primaryColor = Color(0xFF3B82F6)
    val pathColor = Color(0xFFFBBF24) // Yellow like in the image
    
    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No timeline events yet",
                    color = Color(0xFF94A3B8),
                    fontSize = 16.sp
                )
            }
        }
        return
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val isEven = index % 2 == 0
            
            TimelineNodeWithCurve(
                entry = entry,
                isFirst = index == 0,
                isLast = index == entries.lastIndex,
                isTop = isEven,
                pathColor = pathColor,
                nodeColor = primaryColor
            )
        }
    }
}

@Composable
fun TimelineNodeWithCurve(
    entry: TimelineEntry,
    isFirst: Boolean,
    isLast: Boolean,
    isTop: Boolean,
    pathColor: Color,
    nodeColor: Color
) {
    val nodeSize = 48.dp
    val cardWidth = 180.dp
    val curveWidth = 80.dp
    val totalHeight = 320.dp
    
    Column(
        modifier = Modifier.width(cardWidth + curveWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isTop) {
            // Card on top
            TimelineCard(entry = entry, modifier = Modifier.width(cardWidth))
            Spacer(Modifier.height(8.dp))
            
            // Node
            TimelineNode(entry = entry, size = nodeSize, color = nodeColor)
            
            // Curve going down
            if (!isLast) {
                Canvas(modifier = Modifier.size(curveWidth, 100.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        cubicTo(
                            size.width * 0.5f, 0f,
                            size.width * 0.5f, size.height,
                            size.width, size.height
                        )
                    }
                    drawPath(
                        path = path,
                        color = pathColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            
            Spacer(Modifier.height(80.dp))
        } else {
            // Space on top
            Spacer(Modifier.height(80.dp))
            
            // Curve coming from top
            if (!isFirst) {
                Canvas(modifier = Modifier.size(curveWidth, 100.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        cubicTo(
                            size.width * 0.5f, 0f,
                            size.width * 0.5f, size.height,
                            size.width, size.height
                        )
                    }
                    drawPath(
                        path = path,
                        color = pathColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            
            // Node
            TimelineNode(entry = entry, size = nodeSize, color = nodeColor)
            
            Spacer(Modifier.height(8.dp))
            
            // Card on bottom
            TimelineCard(entry = entry, modifier = Modifier.width(cardWidth))
        }
    }
}

@Composable
fun TimelineNode(
    entry: TimelineEntry,
    size: androidx.compose.ui.unit.Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getIconForEventType(entry.eventType),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun TimelineCard(
    entry: TimelineEntry,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (entry.description.isNotEmpty()) {
                Text(
                    text = entry.description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Image if present
            entry.imageUri?.let { uri ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

/**
 * Vertical Timeline with n8n style connected thread and animated dots
 */
@Composable
fun VerticalTimeline(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("No timeline events yet", color = Color(0xFF94A3B8), fontSize = 16.sp)
            }
        }
        return
    }
    
    val lineColor = Color(0xFF3B82F6) // Blue thread color
    
    Column(modifier = modifier.padding(16.dp)) {
        entries.forEachIndexed { index, entry ->
            VerticalTimelineItem(
                entry = entry,
                isFirst = index == 0,
                isLast = index == entries.lastIndex,
                lineColor = lineColor
            )
        }
    }
}

@Composable
fun VerticalTimelineItem(
    entry: TimelineEntry,
    isFirst: Boolean,
    isLast: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    val eventColor = getColorForEventType(entry.eventType)
    
    // Animated dot
    val infiniteTransition = rememberInfiniteTransition(label = "dot_${entry.id}")
    val dotOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotAnim"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline line and node column
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Draw connecting lines with Canvas
            Canvas(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
            ) {
                val centerX = size.width / 2
                val nodeCenter = 24.dp.toPx()
                val nodeRadius = 22.dp.toPx()
                
                // Line coming from top (before node)
                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, nodeCenter - nodeRadius),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                
                // Line going down (after node)
                if (!isLast) {
                    val lineStartY = nodeCenter + nodeRadius
                    val lineEndY = size.height
                    
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, lineStartY),
                        end = Offset(centerX, lineEndY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    // Animated flowing dot on the line below
                    val dotY = lineStartY + (lineEndY - lineStartY) * dotOffset
                    
                    // Glow effect
                    drawCircle(
                        color = lineColor.copy(alpha = 0.5f),
                        radius = 7.dp.toPx(),
                        center = Offset(centerX, dotY)
                    )
                    // White dot
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(centerX, dotY)
                    )
                }
            }
            
            // Node circle on top of lines
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(eventColor.copy(alpha = 0.25f), CircleShape)
                )
                // Main node
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(eventColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForEventType(entry.eventType),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        
        Spacer(Modifier.width(12.dp))
        
        // Content card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Surface(
                        color = eventColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = entry.eventType.name.replace("_", " "),
                            fontSize = 10.sp,
                            color = eventColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                if (entry.description.isNotEmpty()) {
                    Text(
                        text = entry.description,
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
                
                // Image if present
                entry.imageUri?.let { uri ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Text(
                    text = timeFormat.format(Date(entry.timestamp)),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

/**
 * Get icon for event type
 */
fun getIconForEventType(eventType: TimelineEventType) = when (eventType) {
    TimelineEventType.LEAD_CREATED -> Icons.Default.PersonAdd
    TimelineEventType.STATUS_CHANGED -> Icons.Default.Flag
    TimelineEventType.NOTE_ADDED -> Icons.Default.Edit
    TimelineEventType.FOLLOWUP_SCHEDULED -> Icons.Default.Schedule
    TimelineEventType.FOLLOWUP_COMPLETED -> Icons.Default.CheckCircle
    TimelineEventType.CALL_MADE -> Icons.Default.Phone
    TimelineEventType.MESSAGE_SENT -> Icons.Default.Sms
    TimelineEventType.EMAIL_SENT -> Icons.Default.Email
    TimelineEventType.MEETING_SCHEDULED -> Icons.Default.Group
    TimelineEventType.PRODUCT_ASSIGNED -> Icons.Default.Inventory
    TimelineEventType.IMAGE_ADDED -> Icons.Default.Image
    TimelineEventType.CUSTOM_EVENT -> Icons.Default.Event
}

/**
 * Get color for event type
 */
fun getColorForEventType(eventType: TimelineEventType) = when (eventType) {
    TimelineEventType.LEAD_CREATED -> Color(0xFF10B981)
    TimelineEventType.STATUS_CHANGED -> Color(0xFFF59E0B)
    TimelineEventType.NOTE_ADDED -> Color(0xFF8B5CF6)
    TimelineEventType.FOLLOWUP_SCHEDULED -> Color(0xFF3B82F6)
    TimelineEventType.FOLLOWUP_COMPLETED -> Color(0xFF22C55E)
    TimelineEventType.CALL_MADE -> Color(0xFF06B6D4)
    TimelineEventType.MESSAGE_SENT -> Color(0xFF14B8A6)
    TimelineEventType.EMAIL_SENT -> Color(0xFFEC4899)
    TimelineEventType.MEETING_SCHEDULED -> Color(0xFFF97316)
    TimelineEventType.PRODUCT_ASSIGNED -> Color(0xFF6366F1)
    TimelineEventType.IMAGE_ADDED -> Color(0xFFEAB308)
    TimelineEventType.CUSTOM_EVENT -> Color(0xFF64748B)
}
