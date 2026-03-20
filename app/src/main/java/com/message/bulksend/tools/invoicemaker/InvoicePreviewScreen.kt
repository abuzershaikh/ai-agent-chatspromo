package com.message.bulksend.tools.invoicemaker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Colors
private val PrimaryColor = Color(0xFF6366F1)
private val SecondaryColor = Color(0xFF8B5CF6)
private val SuccessColor = Color(0xFF10B981)
private val DangerColor = Color(0xFFEF4444)
private val WarningColor = Color(0xFFF59E0B)
private val BackgroundColor = Color(0xFFF8FAFC)
private val CardColor = Color.White
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePreviewScreen(
    invoice: InvoiceDataTool,
    onBack: () -> Unit,
    onSave: (InvoiceDataTool) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Stamp position state (in dp)
    var stampPosition by remember { mutableStateOf(Offset(200f, 400f)) }
    var showStamp by remember { mutableStateOf(invoice.status == "PAID") }
    var stampRotation by remember { mutableStateOf(-15f) }
    
    val currencyFormat = remember { DecimalFormat("#,##0.00") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    
    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(PrimaryColor, SecondaryColor)),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Invoice Preview", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Drag PAID stamp to position", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    TextButton(
                        onClick = { 
                            val updatedInvoice = invoice.copy(
                                // Save stamp position in invoice data (you can add custom fields)
                            )
                            onSave(updatedInvoice) 
                        }
                    ) {
                        Text("SAVE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Toggle stamp visibility
                FloatingActionButton(
                    onClick = { showStamp = !showStamp },
                    containerColor = if (showStamp) SuccessColor else Color.Gray,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (showStamp) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Stamp",
                        tint = Color.White
                    )
                }
                
                // Reset position
                FloatingActionButton(
                    onClick = { stampPosition = Offset(200f, 400f) },
                    containerColor = WarningColor,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Position", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Invoice Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Invoice Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        InvoicePreviewContent(invoice, currencyFormat, dateFormat)
                    }
                    
                    // Draggable PAID Stamp
                    if (showStamp) {
                        DraggablePaidStamp(
                            position = stampPosition,
                            rotation = stampRotation,
                            onPositionChange = { newPosition ->
                                stampPosition = newPosition
                            },
                            onRotationChange = { newRotation ->
                                stampRotation = newRotation
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InvoicePreviewContent(
    invoice: InvoiceDataTool,
    currencyFormat: DecimalFormat,
    dateFormat: SimpleDateFormat
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    invoice.businessInfo.businessName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor
                )
                if (invoice.businessInfo.address.isNotEmpty()) {
                    Text(invoice.businessInfo.address, fontSize = 14.sp, color = TextSecondary)
                }
                if (invoice.businessInfo.phone.isNotEmpty()) {
                    Text("Phone: ${invoice.businessInfo.phone}", fontSize = 14.sp, color = TextSecondary)
                }
                if (invoice.businessInfo.email.isNotEmpty()) {
                    Text("Email: ${invoice.businessInfo.email}", fontSize = 14.sp, color = TextSecondary)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("INVOICE", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("#${invoice.invoiceNumber}", fontSize = 16.sp, color = TextSecondary)
                Text("Date: ${dateFormat.format(Date(invoice.invoiceDate))}", fontSize = 14.sp, color = TextSecondary)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Bill To
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("BILL TO:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(invoice.clientInfo.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (invoice.clientInfo.address.isNotEmpty()) {
                    Text(invoice.clientInfo.address, fontSize = 14.sp, color = TextSecondary)
                }
                if (invoice.clientInfo.phone.isNotEmpty()) {
                    Text("Phone: ${invoice.clientInfo.phone}", fontSize = 14.sp, color = TextSecondary)
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Items Table
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Table Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DESCRIPTION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(2f))
                    Text("QTY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(0.5f))
                    Text("RATE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text("AMOUNT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(Modifier.height(12.dp))
                
                // Items
                invoice.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.description, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(2f))
                        Text(item.quantity.toString(), fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(0.5f))
                        Text("${invoice.currencySymbol}${currencyFormat.format(item.rate)}", fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("${invoice.currencySymbol}${currencyFormat.format(item.quantity * item.rate)}", fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Totals
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal:", fontSize = 14.sp, color = TextSecondary)
                Text("${invoice.currencySymbol}${currencyFormat.format(invoice.subtotal)}", fontSize = 14.sp, color = TextPrimary)
            }
            
            if (invoice.taxRate > 0) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tax (${invoice.taxRate.toInt()}%):", fontSize = 14.sp, color = TextSecondary)
                    Text("${invoice.currencySymbol}${currencyFormat.format(invoice.taxAmount)}", fontSize = 14.sp, color = TextPrimary)
                }
            }
            
            if (invoice.discount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount:", fontSize = 14.sp, color = TextSecondary)
                    Text("-${invoice.currencySymbol}${currencyFormat.format(invoice.discount)}", fontSize = 14.sp, color = TextPrimary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${invoice.currencySymbol}${currencyFormat.format(invoice.totalAmount)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
            }
        }
        
        if (invoice.notes.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text("Notes:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(invoice.notes, fontSize = 14.sp, color = TextSecondary)
        }
        
        if (invoice.bankDetails.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Bank Details:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(invoice.bankDetails, fontSize = 14.sp, color = TextSecondary)
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun DraggablePaidStamp(
    position: Offset,
    rotation: Float,
    onPositionChange: (Offset) -> Unit,
    onRotationChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .offset { 
                IntOffset(position.x.roundToInt(), position.y.roundToInt()) 
            }
            .size(120.dp, 60.dp)
            .rotate(rotation)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    onPositionChange(position + dragAmount)
                }
            }
    ) {
        // PAID Stamp Design
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 3.dp,
                    color = SuccessColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    SuccessColor.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                )
        ) {
            val paint = android.graphics.Paint().apply {
                color = SuccessColor.toArgb()
                textSize = 48f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                "PAID",
                size.width / 2,
                size.height / 2 + 16f,
                paint
            )
        }
        
        // Drag indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
                .background(SuccessColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Drag",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}