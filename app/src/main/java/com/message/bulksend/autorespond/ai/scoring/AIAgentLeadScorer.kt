package com.message.bulksend.autorespond.ai.scoring

import com.message.bulksend.autorespond.ai.data.model.UserProfile
import com.message.bulksend.autorespond.database.MessageEntity

/**
 * Smart Lead Scoring System
 * Calculates dynamic lead score based on user behavior and interactions
 */
class AIAgentLeadScorer {
    
    /**
     * Calculate lead score (0-100)
     */
    fun calculateLeadScore(profile: UserProfile, interactions: List<MessageEntity>): Int {
        var score = 0
        
        // 1. Base score from existing tier (0-50 points)
        score += when (profile.leadTier) {
            "HOT" -> 50
            "WARM" -> 30
            "COLD" -> 10
            else -> 0
        }
        
        // 2. Interaction frequency - last 7 days (0-30 points)
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val recentInteractions = interactions.filter { it.timestamp > sevenDaysAgo }
        score += minOf(recentInteractions.size * 5, 30)
        
        // 3. Intent analysis (0-30 points)
        val intent = profile.currentIntent?.lowercase() ?: ""
        score += when {
            intent.contains("buy") || intent.contains("purchase") -> 30
            intent.contains("interested") || intent.contains("want") -> 20
            intent.contains("price") || intent.contains("cost") -> 15
            intent.contains("looking") || intent.contains("need") -> 10
            else -> 0
        }
        
        // 4. Response rate (0-20 points)
        if (interactions.isNotEmpty()) {
            val userMessages = interactions.count { it.status == "RECEIVED" }
            val aiMessages = interactions.count { it.outgoingMessage.isNotBlank() }
            if (userMessages > 0) {
                val responseRate = aiMessages.toFloat() / userMessages
                score += (responseRate * 20).toInt()
            }
        }
        
        // 5. Profile completeness bonus (0-10 points)
        var completeness = 0
        if (!profile.name.isNullOrBlank()) completeness += 3
        if (!profile.email.isNullOrBlank()) completeness += 3
        if (!profile.address.isNullOrBlank()) completeness += 2
        if (profile.customData != "{}") completeness += 2
        score += completeness
        
        // 6. Recency bonus (0-10 points)
        if (interactions.isNotEmpty()) {
            val lastInteraction = interactions.maxByOrNull { it.timestamp }
            if (lastInteraction != null) {
                val hoursSinceLastContact = (System.currentTimeMillis() - lastInteraction.timestamp) / (60 * 60 * 1000)
                score += when {
                    hoursSinceLastContact < 24 -> 10  // Last 24 hours
                    hoursSinceLastContact < 72 -> 5   // Last 3 days
                    else -> 0
                }
            }
        }
        
        // Cap at 100
        return minOf(score, 100)
    }
    
    /**
     * Get lead tier based on score
     */
    fun getLeadTier(score: Int): String = when {
        score >= 70 -> "HOT"
        score >= 40 -> "WARM"
        else -> "COLD"
    }
    
    /**
     * Get priority level for follow-ups
     */
    fun getPriority(score: Int): String = when {
        score >= 80 -> "URGENT"
        score >= 60 -> "HIGH"
        score >= 40 -> "MEDIUM"
        else -> "LOW"
    }
    
    /**
     * Calculate score improvement
     */
    fun calculateScoreImprovement(oldScore: Int, newScore: Int): Int {
        return newScore - oldScore
    }
    
    /**
     * Get recommended follow-up time in hours
     */
    fun getRecommendedFollowUpTime(score: Int, intent: String?): Long {
        val baseTime = when {
            score >= 70 -> 2L   // 2 hours for hot leads
            score >= 40 -> 24L  // 1 day for warm leads
            else -> 72L         // 3 days for cold leads
        }
        
        // Adjust based on intent
        val intentMultiplier = when {
            intent?.contains("urgent", ignoreCase = true) == true -> 0.5
            intent?.contains("buy", ignoreCase = true) == true -> 0.7
            intent?.contains("interested", ignoreCase = true) == true -> 1.0
            else -> 1.5
        }
        
        return (baseTime * intentMultiplier).toLong()
    }
}
