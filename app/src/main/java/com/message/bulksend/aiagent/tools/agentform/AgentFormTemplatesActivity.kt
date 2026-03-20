package com.message.bulksend.aiagent.tools.agentform

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.aiagent.tools.agentform.screens.AgentFormMainScreen
import com.message.bulksend.userdetails.UserDetailsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentFormTemplatesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = UserDetailsPreferences(this)
        val phone = sanitizePhone(prefs.getPhoneNumber())
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AgentFormPredefinedTemplates.seedIfNeeded(this@AgentFormTemplatesActivity)
                AgentFormTableSheetSyncManager(this@AgentFormTemplatesActivity).initializeAgentFormSheetSystem()
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    AgentFormMainScreen(
                            onBack = { finish() },
                            ownerUid = uid,
                            ownerPhone = phone,
                            onCreateNew = {
                                val intent = Intent(this, AgentFormBuilderActivity::class.java)
                                intent.putExtra("PHONE", phone)
                                intent.putExtra("UID", uid)
                                startActivity(intent)
                            },
                            onEdit = { formId ->
                                val intent = Intent(this, AgentFormBuilderActivity::class.java)
                                intent.putExtra("FORM_ID", formId)
                                intent.putExtra("PHONE", phone)
                                intent.putExtra("UID", uid)
                                startActivity(intent)
                            }
                    )
                }
            }
        }
    }

    private fun sanitizePhone(value: String?): String {
        return value.orEmpty().replace(Regex("[^0-9]"), "")
    }
}
