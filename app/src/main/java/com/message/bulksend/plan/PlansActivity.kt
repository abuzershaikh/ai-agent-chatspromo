package com.message.bulksend.plan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Compatibility entry point for stale navigation paths.
 * Redirects old plan launcher targets into the new first plan screen.
 */
class PlansActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, PrepackActivity::class.java))
        finish()
    }
}
