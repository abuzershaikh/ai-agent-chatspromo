package com.message.bulksend.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.contactmanager.Contact

import com.message.bulksend.data.ContactStatus


data class CampaignProgress(
    val campaignId: String,
    val campaignName: String,
    val campaignType: String, // "BULKSEND", "BULKTEXT", "TEXTMEDIA", "SHEETSEND", "FORMCAMPAIGN"
    val message: String,
    val totalContacts: Int,
    val sentCount: Int,
    val failedCount: Int,
    val remainingCount: Int,
    val currentIndex: Int,
    val isRunning: Boolean,
    val isStopped: Boolean,
    val contactStatuses: List<ContactStatus> = emptyList(), // Contact-level status tracking
    val errorMessage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

object CampaignProgressManager {
    private const val PREFS_NAME = "campaign_progress_prefs"
    private const val KEY_PROGRESS_PREFIX = "progress_"
    private const val KEY_STOP_FLAG_PREFIX = "stop_flag_"
    private val gson = Gson()

    fun saveProgress(context: Context, progress: CampaignProgress) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = gson.toJson(progress)
        // Use compound key: campaignId + campaignType for complete isolation
        val key = "${progress.campaignId}_${progress.campaignType}"
        prefs.edit().putString(KEY_PROGRESS_PREFIX + key, jsonString).apply()
    }

    fun getProgress(context: Context, campaignId: String): CampaignProgress? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PROGRESS_PREFIX + campaignId, null)
        return if (jsonString != null) {
            try {
                gson.fromJson(jsonString, CampaignProgress::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun getProgressForType(context: Context, campaignId: String, campaignType: String): CampaignProgress? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Use compound key for type-specific lookup
        val key = "${campaignId}_${campaignType}"
        val jsonString = prefs.getString(KEY_PROGRESS_PREFIX + key, null)
        return if (jsonString != null) {
            try {
                gson.fromJson(jsonString, CampaignProgress::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearProgress(context: Context, campaignId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PROGRESS_PREFIX + campaignId)
            .remove(KEY_STOP_FLAG_PREFIX + campaignId)
            .apply()
    }

    fun clearProgressForType(context: Context, campaignId: String, campaignType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${campaignId}_${campaignType}"
        prefs.edit()
            .remove(KEY_PROGRESS_PREFIX + key)
            .remove(KEY_STOP_FLAG_PREFIX + key)
            .apply()
    }

    fun setStopFlag(context: Context, campaignId: String, shouldStop: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_STOP_FLAG_PREFIX + campaignId, shouldStop).apply()
    }

    fun setStopFlagForType(context: Context, campaignId: String, campaignType: String, shouldStop: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${campaignId}_${campaignType}"
        prefs.edit().putBoolean(KEY_STOP_FLAG_PREFIX + key, shouldStop).apply()
    }

    fun shouldStop(context: Context, campaignId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_STOP_FLAG_PREFIX + campaignId, false)
    }

    fun shouldStopForType(context: Context, campaignId: String, campaignType: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${campaignId}_${campaignType}"
        return prefs.getBoolean(KEY_STOP_FLAG_PREFIX + key, false)
    }

    fun getAllProgress(context: Context): List<CampaignProgress> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val progressList = mutableListOf<CampaignProgress>()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PROGRESS_PREFIX) && value is String) {
                try {
                    val progress = gson.fromJson(value, CampaignProgress::class.java)
                    progressList.add(progress)
                } catch (e: Exception) {
                    // Ignore invalid entries
                }
            }
        }

        return progressList.sortedByDescending { it.lastUpdated }
    }

    fun hasResumableProgress(context: Context, campaignId: String): Boolean {
        val progress = getProgress(context, campaignId)
        return progress != null && progress.isStopped && progress.remainingCount > 0
    }

    fun getResumableProgress(context: Context, campaignId: String): CampaignProgress? {
        val progress = getProgress(context, campaignId)
        return if (progress != null && progress.isStopped && progress.remainingCount > 0) {
            progress
        } else {
            null
        }
    }

    fun getResumableProgressForType(context: Context, campaignId: String, campaignType: String): CampaignProgress? {
        val progress = getProgressForType(context, campaignId, campaignType)
        return if (progress != null && progress.isStopped && progress.remainingCount > 0) {
            progress
        } else {
            null
        }
    }

    fun getAllResumableProgress(context: Context): List<CampaignProgress> {
        return getAllProgress(context).filter { it.isStopped && it.remainingCount > 0 }
    }

    // Get resumable campaigns only for specific campaign type
    fun getAllResumableProgressForType(context: Context, campaignType: String): List<CampaignProgress> {
        return getAllProgress(context).filter {
            it.isStopped && it.remainingCount > 0 && it.campaignType == campaignType
        }
    }

    // Check if there's any resumable campaign for specific type
    fun hasResumableProgressForType(context: Context, campaignType: String): Boolean {
        return getAllResumableProgressForType(context, campaignType).isNotEmpty()
    }

    // Get the most recent resumable campaign for specific type
    fun getLatestResumableProgressForType(context: Context, campaignType: String): CampaignProgress? {
        return getAllResumableProgressForType(context, campaignType).firstOrNull()
    }

    fun getContactStatuses(context: Context, campaignId: String): List<ContactStatus> {
        val progress = getProgress(context, campaignId)
        return progress?.contactStatuses ?: emptyList()
    }

    fun getContactStatusesForType(context: Context, campaignId: String, campaignType: String): List<ContactStatus> {
        val progress = getProgressForType(context, campaignId, campaignType)
        return progress?.contactStatuses ?: emptyList()
    }

    fun updateContactStatus(context: Context, campaignId: String, contactNumber: String, status: String) {
        val progress = getProgress(context, campaignId)
        if (progress != null) {
            val updatedStatuses = progress.contactStatuses.map { contactStatus ->
                if (contactStatus.number == contactNumber) {
                    contactStatus.copy(status = status)
                } else {
                    contactStatus
                }
            }
            val updatedProgress = progress.copy(
                contactStatuses = updatedStatuses,
                sentCount = updatedStatuses.count { it.status == "sent" },
                failedCount = updatedStatuses.count { it.status == "failed" },
                remainingCount = updatedStatuses.count { it.status == "pending" },
                lastUpdated = System.currentTimeMillis()
            )
            saveProgress(context, updatedProgress)
        }
    }

    fun updateContactStatusForType(context: Context, campaignId: String, campaignType: String, contactNumber: String, status: String) {
        val progress = getProgressForType(context, campaignId, campaignType)
        if (progress != null) {
            val updatedStatuses = progress.contactStatuses.map { contactStatus ->
                if (contactStatus.number == contactNumber) {
                    contactStatus.copy(status = status)
                } else {
                    contactStatus
                }
            }
            val updatedProgress = progress.copy(
                contactStatuses = updatedStatuses,
                sentCount = updatedStatuses.count { it.status == "sent" },
                failedCount = updatedStatuses.count { it.status == "failed" },
                remainingCount = updatedStatuses.count { it.status == "pending" },
                lastUpdated = System.currentTimeMillis()
            )
            saveProgress(context, updatedProgress)
        }
    }

    fun initializeContactStatuses(context: Context, campaignId: String, contacts: List<Contact>) {
        val progress = getProgress(context, campaignId)
        if (progress != null) {
            val contactStatuses = contacts.map { contact ->
                ContactStatus(contact.number, "pending")
            }
            val updatedProgress = progress.copy(
                contactStatuses = contactStatuses,
                totalContacts = contacts.size,
                remainingCount = contacts.size,
                sentCount = 0,
                failedCount = 0
            )
            saveProgress(context, updatedProgress)
        }
    }

    fun initializeContactStatusesForType(context: Context, campaignId: String, campaignType: String, contacts: List<Contact>) {
        val progress = getProgressForType(context, campaignId, campaignType)
        if (progress != null) {
            val contactStatuses = contacts.map { contact ->
                ContactStatus(contact.number, "pending")
            }
            val updatedProgress = progress.copy(
                contactStatuses = contactStatuses,
                totalContacts = contacts.size,
                remainingCount = contacts.size,
                sentCount = 0,
                failedCount = 0
            )
            saveProgress(context, updatedProgress)
        }
    }

    fun markAsResumed(context: Context, campaignId: String) {
        val progress = getProgress(context, campaignId)
        if (progress != null) {
            val resumedProgress = progress.copy(
                isRunning = true,
                isStopped = false,
                lastUpdated = System.currentTimeMillis()
            )
            saveProgress(context, resumedProgress)
        }
    }

    fun markAsResumedForType(context: Context, campaignId: String, campaignType: String) {
        val progress = getProgressForType(context, campaignId, campaignType)
        if (progress != null) {
            val resumedProgress = progress.copy(
                isRunning = true,
                isStopped = false,
                lastUpdated = System.currentTimeMillis()
            )
            saveProgress(context, resumedProgress)
        }
    }
}