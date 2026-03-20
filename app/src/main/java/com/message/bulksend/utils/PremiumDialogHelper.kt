package com.message.bulksend.utils

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.message.bulksend.plan.PrepackActivity

/**
 * Premium Upgrade Dialog
 * Shows when free user reaches contact limit
 */
@Composable
fun PremiumUpgradeDialog(
    currentContacts: Int,
    contactsLimit: Int,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit = {}
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Diamond,
                contentDescription = "Premium",
                tint = Color(0xFFFFD700), // Gold color
                modifier = androidx.compose.ui.Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "🚫 Contact Limit Reached!",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You've reached your free plan limit:",
                    fontSize = 16.sp
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = androidx.compose.ui.Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$currentContacts / $contactsLimit contacts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                
                Text(
                    text = "💎 Upgrade to Premium for:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                
                Column(
                    modifier = androidx.compose.ui.Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✅ Unlimited contacts")
                    Text("✅ Unlimited groups")
                    Text("✅ Priority support")
                    Text("✅ Advanced features")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Redirect to plan purchase flow
                    val intent = Intent(context, PrepackActivity::class.java)
                    context.startActivity(intent)
                    onUpgrade()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Diamond,
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.size(20.dp)
                )
                Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                Text(
                    "Get Premium",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}

/**
 * Contact Limit Warning Dialog
 * Shows when user is about to exceed limit
 */
@Composable
fun ContactLimitWarningDialog(
    contactsToAdd: Int,
    availableSlots: Int,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onUpgrade: () -> Unit = {}
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Diamond,
                contentDescription = "Warning",
                tint = Color(0xFFFFA500), // Orange color
                modifier = androidx.compose.ui.Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "⚠️ Partial Import",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You're trying to add $contactsToAdd contacts, but only $availableSlots slots are available.",
                    fontSize = 16.sp
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = androidx.compose.ui.Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Will save: $availableSlots contacts",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Will skip: ${contactsToAdd - availableSlots} contacts",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Text(
                    text = "💎 Upgrade to Premium to save all contacts!",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continue with $availableSlots")
            }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        // Redirect to plan purchase flow
                        val intent = Intent(context, PrepackActivity::class.java)
                        context.startActivity(intent)
                        onUpgrade()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = null,
                        modifier = androidx.compose.ui.Modifier.size(16.dp),
                        tint = Color(0xFFFFD700)
                    )
                    Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
                    Text("Get Premium")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
