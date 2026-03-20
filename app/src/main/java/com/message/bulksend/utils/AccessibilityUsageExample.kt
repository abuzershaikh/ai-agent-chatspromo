package com.message.bulksend.utils

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

/**
 * Example usage of AccessibilityPermissionDialog (Jetpack Compose Version)
 * 
 * Yeh file examples ke liye hai - Compose me kaise use karna hai
 */

/**
 * Example 1: Basic Usage in Compose Screen
 */
@Composable
fun ExampleScreen1() {
    val context = LocalContext.current
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    // Button ya koi trigger
    androidx.compose.material3.Button(onClick = {
        if (!isAccessibilityServiceEnabled(context)) {
            showAccessibilityDialog = true
        } else {
            Toast.makeText(context, "Permission already enabled!", Toast.LENGTH_SHORT).show()
        }
    }) {
        androidx.compose.material3.Text("Check Permission")
    }

    // Dialog
    if (showAccessibilityDialog) {
        AccessibilityPermissionDialog(
            onAgree = {
                Toast.makeText(context, "Settings me jakar permission enable karein", Toast.LENGTH_SHORT).show()
            },
            onDisagree = {
                Toast.makeText(context, "Permission zaroori hai", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showAccessibilityDialog = false
            }
        )
    }
}

/**
 * Example 2: Campaign Launch ke saath
 */
@Composable
fun ExampleCampaignScreen() {
    val context = LocalContext.current
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.Button(onClick = {
        // Check permission before launching campaign
        if (!isAccessibilityServiceEnabled(context)) {
            showAccessibilityDialog = true
            return@Button
        }

        // Launch campaign
        Toast.makeText(context, "Campaign launching...", Toast.LENGTH_SHORT).show()
    }) {
        androidx.compose.material3.Text("Launch Campaign")
    }

    // Dialog
    if (showAccessibilityDialog) {
        AccessibilityPermissionDialog(
            onAgree = {
                Toast.makeText(context, "Please enable permission in settings", Toast.LENGTH_SHORT).show()
            },
            onDisagree = {
                Toast.makeText(context, "Cannot proceed without permission", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showAccessibilityDialog = false
            }
        )
    }
}

/**
 * Example 3: Helper function ke saath
 */
@Composable
fun ExampleWithHelper() {
    val context = LocalContext.current
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val serviceName = "com.message.bulksend.bulksend.WhatsAppAutoSendService"

    androidx.compose.material3.Button(onClick = {
        if (AccessibilityHelper.checkAccessibilityPermission(context, serviceName)) {
            Toast.makeText(context, "Permission enabled!", Toast.LENGTH_SHORT).show()
        } else {
            showAccessibilityDialog = true
        }
    }) {
        androidx.compose.material3.Text("Check with Helper")
    }

    if (showAccessibilityDialog) {
        AccessibilityPermissionDialog(
            onAgree = { /* action */ },
            onDisagree = { /* action */ },
            onDismiss = { showAccessibilityDialog = false }
        )
    }
}
