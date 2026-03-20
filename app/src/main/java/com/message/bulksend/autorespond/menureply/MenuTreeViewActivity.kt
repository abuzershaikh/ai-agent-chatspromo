package com.message.bulksend.autorespond.menureply

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.message.bulksend.ui.theme.BulksendTestTheme

/**
 * Menu Tree View Activity - Redirects to MenuReplyActivity
 * Kept for backward compatibility
 */
class MenuTreeViewActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Redirect to MenuReplyActivity which now shows the tree view
        val intent = Intent(this, MenuReplyActivity::class.java)
        startActivity(intent)
        finish()
    }
}