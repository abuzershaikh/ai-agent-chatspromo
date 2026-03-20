package com.message.bulksend.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.message.bulksend.data.UserData
import com.message.bulksend.utils.DeviceUtils

class EmailService(private val context: Context) {
    
    companion object {
        private const val TAG = "EmailService"
    }
    
    /**
     * Send welcome email with device information
     * DISABLED: Email notifications are turned off
     */
    fun sendWelcomeEmail(userData: UserData) {
        // Email functionality disabled
        Log.d(TAG, "Welcome email disabled for: ${userData.email}")
    }
    
    /**
     * Send device change notification email
     * DISABLED: Email notifications are turned off
     */
    fun sendDeviceChangeNotification(userData: UserData) {
        // Email functionality disabled
        Log.d(TAG, "Device change notification disabled for: ${userData.email}")
    }
    
    /**
     * Create welcome email body
     */
    private fun createWelcomeEmailBody(userData: UserData): String {
        return """
            Hello ${userData.displayName},
            
            Welcome to ChatsPromo! Your account has been successfully created.
            
            Account Details:
            • Registration Date: ${userData.firstSignupDate.toDate()}
            • Account Status: Active
            
            Device Information:
            • Device Model: ${userData.deviceInfo.model}
            • OS Version: ${userData.deviceInfo.osVersion}
            • App Version: ${userData.deviceInfo.appVersion}
            
            Security Information:
            Your account is now linked to this device. For security reasons, you can only be logged in on one device at a time.
            
            If you didn't create this account or notice any suspicious activity, please contact our support team immediately.
            
            Thank you for choosing ChatsPromo!
            
            Best regards,
            ChatsPromo Team
        """.trimIndent()
    }
    
    /**
     * Create device change email body
     */
    private fun createDeviceChangeEmailBody(userData: UserData): String {
        val lastLogin = userData.loginHistory.lastOrNull()
        return """
            Hello ${userData.displayName},
            
            We detected a login to your ChatsPromo account from a new device.
            
            Login Details:
            • Login Time: ${userData.lastLoginDate.toDate()}
            • Device Model: ${userData.deviceInfo.model}
            • OS Version: ${userData.deviceInfo.osVersion}
            • App Version: ${userData.deviceInfo.appVersion}
            
            ${lastLogin?.let { 
                """
                Previous Login:
                • Device: ${it.deviceModel}
                • Time: ${it.loginAt.toDate()}
                • Location: ${it.location}
                """.trimIndent()
            } ?: ""}
            
            Security Notice:
            Your previous device session has been automatically logged out for security reasons.
            
            If this wasn't you, please:
            1. Change your Google account password immediately
            2. Contact our support team
            3. Review your account activity
            
            If this was you, you can safely ignore this email.
            
            Best regards,
            ChatsPromo Team
        """.trimIndent()
    }
    
    /**
     * Send email using device's default email app
     */
    private fun sendEmail(to: String, subject: String, body: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Log.w(TAG, "No email app found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening email app", e)
        }
    }
    
    /**
     * Send support email
     */
    fun sendSupportEmail(userEmail: String, issue: String) {
        try {
            val subject = "Zestbot Support Request - Device Issue"
            val body = """
                User Email: $userEmail
                Device ID: ${DeviceUtils.getDeviceId(context)}
                Device Model: ${DeviceUtils.getDeviceModel()}
                Android Version: ${DeviceUtils.getAndroidVersion()}
                App Version: ${DeviceUtils.getAppVersion(context)}
                
                Issue Description:
                $issue
            """.trimIndent()
            
            sendEmail("support@zestbot.com", subject, body) // Replace with your support email
        } catch (e: Exception) {
            Log.e(TAG, "Error sending support email", e)
        }
    }
}