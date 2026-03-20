package com.message.bulksend.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.google.firebase.Timestamp
import com.message.bulksend.data.DeviceInfo
import com.message.bulksend.data.LoginHistoryItem
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.*

object DeviceUtils {

    /**
     * Get unique device ID (Firebase Installation ID format)
     * Uses ANDROID_ID as base to maintain consistency across app reinstalls
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return try {
            // Use ANDROID_ID directly as it persists across app reinstalls
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            // Create a consistent device ID using hash of ANDROID_ID
            val hashedId = hashString(androidId).take(9)
            "fid_$hashedId"
        } catch (e: Exception) {
            // Fallback to a generated UUID if ANDROID_ID is not available
            "fid_${generateRandomString(9)}"
        }
    }

    /**
     * Hash string using SHA-256
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate random string for device ID
     */
    private fun generateRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * Get device model information
     */
    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }

    /**
     * Get Android version with API level
     */
    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE}"
    }

    /**
     * Get detailed OS version
     */
    fun getDetailedOSVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    /**
     * Generate unique identifier for user
     */
    fun generateUniqueIdentifier(email: String, deviceId: String): String {
        val input = "$email-$deviceId-${System.currentTimeMillis()}"
        return hashString(input).take(12).uppercase()
    }

    /**
     * Get app version
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Create enhanced device info object
     */
    fun createDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            model = getDeviceModel(),
            osVersion = getAndroidVersion(),
            appVersion = getAppVersion(context),
            lastActiveDate = Timestamp.now()
        )
    }

    /**
     * Create login history item
     */
    fun createLoginHistoryItem(context: Context, deviceId: String): LoginHistoryItem {
        return LoginHistoryItem(
            deviceId = deviceId,
            loginAt = Timestamp.now(),
            deviceModel = getDeviceModel(),
            osVersion = getAndroidVersion(),
            ipAddress = getLocalIpAddress(),
            location = getApproximateLocation(context)
        )
    }

    /**
     * Get local IP address
     */
    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress
                        if (hostAddress?.contains(':') == false) { // IPv4
                            return hostAddress
                        }
                    }
                }
            }
            "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get approximate location (country/region)
     */
    private fun getApproximateLocation(context: Context): String {
        return try {
            val locale = context.resources.configuration.locales[0]
            locale.displayCountry
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Check if device is connected to internet
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    /**
     * Get device RAM information
     */
    fun getDeviceRAM(context: Context): String {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRAM = memoryInfo.totalMem / (1024 * 1024 * 1024) // Convert to GB
            "${totalRAM}GB RAM"
        } catch (e: Exception) {
            "Unknown RAM"
        }
    }

    /**
     * Get device storage information
     */
    fun getDeviceStorage(): String {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val totalGB = totalBytes / (1024 * 1024 * 1024)
            "${totalGB}GB Storage"
        } catch (e: Exception) {
            "Unknown Storage"
        }
    }
}