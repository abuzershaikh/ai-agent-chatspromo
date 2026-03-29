package com.message.bulksend.autorespond.documentreply

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Info dialog for Document Reply feature with step-by-step instructions
 */
object DocumentReplyInfoDialog {
    
    private const val TAG = "DocumentReplyInfo"
    
    /**
     * Show Document Reply info dialog with step-by-step guide
     */
    @Composable
    fun ShowDocumentReplyInfoDialog(
        onDismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            DocumentReplyInfoContent(onDismiss = onDismiss)
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DocumentReplyInfoContent(
        onDismiss: () -> Unit
    ) {
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📄 Document Reply Guide",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00D4FF)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF00D4FF)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Step-by-step guide with Important Notice
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Important Notice as first item
                        item {
                            ImportantNoticeCard()
                        }
                        
                        // Step-by-step guide
                        itemsIndexed(getDocumentReplySteps()) { index, step ->
                            StepCard(
                                stepNumber = index + 1,
                                step = step
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Close button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D4FF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Got It!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ImportantNoticeCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722).copy(alpha = 0.1f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5722))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Important",
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "⚠️ Important Notice",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🔓 Document Reply only works when your phone is UNLOCKED!\n\n" +
                    "📱 The feature needs to open WhatsApp and send documents automatically, " +
                    "which requires your phone to be unlocked and accessible.\n\n" +
                    "🔒 If your phone is locked, document replies will not work.",
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                )
            }
        }
    }
    
    @Composable
    private fun StepCard(
        stepNumber: Int,
        step: DocumentReplyStep
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Step number circle
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF00D4FF)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stepNumber.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Step content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            step.icon,
                            contentDescription = step.title,
                            tint = step.iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            step.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        step.description,
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
    
    /**
     * Get step-by-step instructions for Document Reply
     */
    private fun getDocumentReplySteps(): List<DocumentReplyStep> {
        return listOf(
            DocumentReplyStep(
                icon = Icons.Default.LockOpen,
                iconColor = Color(0xFF4CAF50),
                title = "Keep Phone Unlocked",
                description = "Make sure your phone is unlocked when expecting messages. Document Reply cannot work on lock screen as it needs to open WhatsApp automatically."
            ),
            DocumentReplyStep(
                icon = Icons.Default.Add,
                iconColor = Color(0xFF00D4FF),
                title = "Add Documents",
                description = "Tap the + button to add documents. You can add Images, Videos, PDFs, and Audio files. Use the document type cards or the multiple file picker."
            ),
            DocumentReplyStep(
                icon = Icons.Default.Edit,
                iconColor = Color(0xFF9C27B0),
                title = "Create Document Reply",
                description = "Set a keyword (like 'brochure', 'price list') and select documents to send. Choose 'exact' match for specific words or 'contains' for flexible matching."
            ),
            DocumentReplyStep(
                icon = Icons.Default.Settings,
                iconColor = Color(0xFFFF9800),
                title = "Grant Permissions",
                description = "Make sure notification access and accessibility permissions are granted."
            ),
            DocumentReplyStep(
                icon = Icons.Default.Notifications,
                iconColor = Color(0xFF2196F3),
                title = "Receive Messages",
                description = "When someone sends a message with your keyword, the app will automatically detect it and prepare to send the documents."
            ),
            DocumentReplyStep(
                icon = Icons.Default.Send,
                iconColor = Color(0xFF4CAF50),
                title = "Automatic Sending",
                description = "The app will open WhatsApp, navigate to the chat, and send your documents automatically. Multiple documents are sent one by one with proper delays."
            ),
            DocumentReplyStep(
                icon = Icons.Default.CheckCircle,
                iconColor = Color(0xFF4CAF50),
                title = "Success!",
                description = "Your documents are sent automatically! The app returns to home screen after sending. Check WhatsApp to confirm delivery."
            )
        )
    }
}

/**
 * Data class for Document Reply step information
 */
data class DocumentReplyStep(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val description: String
)
