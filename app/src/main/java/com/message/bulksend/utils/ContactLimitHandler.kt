package com.message.bulksend.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.message.bulksend.auth.UserProfileActivity

import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.contactmanager.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Contact Limit Handler
 * Handles contact saving with automatic premium dialog display
 */
object ContactLimitHandler {
    
    /**
     * Save group with automatic limit checking and dialog display
     */
    suspend fun saveGroupWithLimitCheck(
        context: Context,
        repository: ContactsRepository,
        groupName: String,
        contacts: List<Contact>,
        onSuccess: (String) -> Unit,
        onLimitReached: (currentContacts: Int, limit: Int) -> Unit,
        onPartialSave: (saved: Int, skipped: Int) -> Unit
    ) {
        val result = repository.saveGroup(groupName, contacts)
        
        result.onSuccess { message ->
            // Check if it's a partial save
            if (message.contains("skipped")) {
                // Extract numbers from message if possible
                val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
                val currentContacts = subscriptionInfo["currentContacts"] as? Int ?: 0
                val contactsLimit = subscriptionInfo["contactsLimit"] as? Int ?: 10
                val saved = contactsLimit - (currentContacts - contacts.size)
                val skipped = contacts.size - saved
                onPartialSave(saved, skipped)
            } else {
                onSuccess(message)
            }
        }.onFailure { error ->
            val errorMessage = error.message ?: "Unknown error"
            
            // Check if it's a limit error
            if (errorMessage.contains("Contact limit reached") || 
                errorMessage.contains("limit exceeded")) {
                // Get current subscription info
                val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
                val currentContacts = subscriptionInfo["currentContacts"] as? Int ?: 0
                val contactsLimit = subscriptionInfo["contactsLimit"] as? Int ?: 10
                
                onLimitReached(currentContacts, contactsLimit)
            } else {
                // Other errors - show toast
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Composable wrapper for contact saving with dialogs
 */
@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun ContactSaveHandler(
    context: Context,
    repository: ContactsRepository,
    scope: CoroutineScope,
    groupName: String,
    contacts: List<Contact>,
    onSaveComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPremiumDialog by remember { mutableStateOf(false) }
    var currentContactCount by remember { mutableStateOf(0) }
    var contactLimit by remember { mutableStateOf(10) }
    
    // Trigger save
    scope.launch {
        ContactLimitHandler.saveGroupWithLimitCheck(
            context = context,
            repository = repository,
            groupName = groupName,
            contacts = contacts,
            onSuccess = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                onSaveComplete()
            },
            onLimitReached = { current, limit ->
                currentContactCount = current
                contactLimit = limit
                showPremiumDialog = true
            },
            onPartialSave = { saved, skipped ->
                Toast.makeText(
                    context,
                    "✅ Saved $saved contacts\n⚠️ Skipped $skipped contacts (limit reached)\n\n💎 Upgrade to Premium for unlimited!",
                    Toast.LENGTH_LONG
                ).show()
                onSaveComplete()
            }
        )
    }
    
    // Show premium dialog if limit reached
    if (showPremiumDialog) {
        PremiumUpgradeDialog(
            currentContacts = currentContactCount,
            contactsLimit = contactLimit,
            onDismiss = {
                showPremiumDialog = false
                onDismiss()
            },
            onUpgrade = {
                try {
                    val intent = Intent(context, UserProfileActivity::class.java)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Admin panel not available", Toast.LENGTH_SHORT).show()
                }
                showPremiumDialog = false
                onDismiss()
            }
        )
    }
}

/**
 * Extension function for easy Result handling with dialog
 */
fun Result<String>.handleWithDialog(
    context: Context,
    onSuccess: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    },
    onLimitReached: () -> Unit
) {
    this.onSuccess { message ->
        onSuccess(message)
    }.onFailure { error ->
        val errorMessage = error.message ?: "Unknown error"
        
        if (errorMessage.contains("Contact limit reached") || 
            errorMessage.contains("limit exceeded")) {
            onLimitReached()
        } else {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }
}
