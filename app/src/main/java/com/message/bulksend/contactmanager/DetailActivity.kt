package com.message.bulksend.contactmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class DetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Redirecting to the new CampaignstatusActivity
        val intent = Intent(this, ContactzActivity::class.java)
        startActivity(intent)
        finish()
    }
}
