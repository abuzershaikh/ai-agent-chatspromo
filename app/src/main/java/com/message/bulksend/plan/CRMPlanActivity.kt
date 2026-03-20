package com.message.bulksend.plan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class CRMPlanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, PrepackActivity::class.java))
        finish()
    }
}
