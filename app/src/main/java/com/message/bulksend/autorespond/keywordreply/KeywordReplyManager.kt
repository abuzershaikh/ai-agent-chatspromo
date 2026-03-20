package com.message.bulksend.autorespond.keywordreply

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager class for keyword replies
 */
class KeywordReplyManager(private val context: Context) {

    companion object {
        const val TAG = "KeywordReplyManager"
        private const val PREFS_NAME = "keyword_reply_prefs"
        private const val KEY_REPLIES = "keyword_replies"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Save a keyword reply
     */
    fun saveKeywordReply(reply: KeywordReplyData) {
        val replies = getAllReplies().toMutableList()
        
        // Check if updating existing reply
        val existingIndex = replies.indexOfFirst { it.id == reply.id }
        if (existingIndex != -1) {
            replies[existingIndex] = reply
        } else {
            replies.add(0, reply) // Add to beginning
        }
        
        val json = gson.toJson(replies)
        prefs.edit().putString(KEY_REPLIES, json).apply()
        Log.d(TAG, "Keyword reply saved: ${reply.incomingKeyword}")
    }

    /**
     * Get all keyword replies
     */
    fun getAllReplies(): List<KeywordReplyData> {
        val json = prefs.getString(KEY_REPLIES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<KeywordReplyData>>() {}.type
            val replies: List<KeywordReplyData> = gson.fromJson(json, type)
            
            // Migration: Ensure all replies have proper minWordMatch value
            val migratedReplies = replies.map { reply ->
                if (reply.matchOption == "contains" && reply.minWordMatch <= 0) {
                    // Set default to 1 for old replies
                    reply.copy(minWordMatch = 1)
                } else {
                    reply
                }
            }
            
            // Save migrated data if any changes were made
            if (migratedReplies != replies) {
                val migratedJson = gson.toJson(migratedReplies)
                prefs.edit().putString(KEY_REPLIES, migratedJson).apply()
                Log.d(TAG, "Migrated ${migratedReplies.size} keyword replies")
            }
            
            migratedReplies
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing replies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Delete a keyword reply
     */
    fun deleteReply(id: String) {
        val replies = getAllReplies().toMutableList()
        replies.removeAll { it.id == id }
        val json = gson.toJson(replies)
        prefs.edit().putString(KEY_REPLIES, json).apply()
        Log.d(TAG, "Keyword reply deleted: $id")
    }

    /**
     * Find matching reply for incoming message
     */
    fun findMatchingReply(incomingMessage: String): KeywordReplyData? {
        val replies = getAllReplies().filter { it.isEnabled }
        
        for (reply in replies) {
            val matches = when (reply.matchOption) {
                "exact" -> incomingMessage.trim().equals(reply.incomingKeyword.trim(), ignoreCase = true)
                "contains" -> {
                    // Split keyword into individual words and count matches
                    val keywords = reply.incomingKeyword.trim().split("\\s+".toRegex())
                    val matchCount = keywords.count { keyword ->
                        incomingMessage.contains(keyword, ignoreCase = true)
                    }
                    
                    Log.d(TAG, "Contains match check:")
                    Log.d(TAG, "  Incoming: $incomingMessage")
                    Log.d(TAG, "  Keywords: $keywords")
                    Log.d(TAG, "  Match count: $matchCount")
                    Log.d(TAG, "  Required: ${reply.minWordMatch}")
                    
                    // Check if match count meets minimum requirement
                    val result = matchCount >= reply.minWordMatch
                    Log.d(TAG, "  Result: $result")
                    result
                }
                else -> false
            }
            
            if (matches) {
                Log.d(TAG, "✅ Match found: ${reply.incomingKeyword} (minWordMatch: ${reply.minWordMatch})")
                return reply
            }
        }
        
        Log.d(TAG, "❌ No match found for: $incomingMessage")
        return null
    }

    /**
     * Toggle reply enabled state
     */
    fun toggleReplyEnabled(id: String) {
        val replies = getAllReplies().toMutableList()
        val index = replies.indexOfFirst { it.id == id }
        if (index != -1) {
            replies[index] = replies[index].copy(isEnabled = !replies[index].isEnabled)
            val json = gson.toJson(replies)
            prefs.edit().putString(KEY_REPLIES, json).apply()
        }
    }
}
