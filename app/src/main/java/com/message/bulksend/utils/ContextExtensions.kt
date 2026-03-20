package com.message.bulksend.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Safely unwrap an Activity from any Context/ContextWrapper chain.
 */
tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }
}
