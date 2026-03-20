package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.data.repo.AIAgentRepository
import com.message.bulksend.autorespond.ai.scoring.AIAgentLeadScorer

/**
 * Handler for updating lead scores after conversation
 */
class LeadScoringHandler(
    private val aiAgentRepo: AIAgentRepository,
    private val leadScorer: AIAgentLeadScorer
) : MessageHandler {
    
    override fun getPriority(): Int = 200 // Execute late
    
    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        try {
            val profile = aiAgentRepo.getUserProfile(senderPhone) ?: return HandlerResult(success = true)
            val interactions = aiAgentRepo.getConversationHistory(senderPhone, limit = 20)
            
            val oldScore = profile.leadScore
            val newScore = leadScorer.calculateLeadScore(profile, interactions)
            val newTier = leadScorer.getLeadTier(newScore)
            
            val updatedProfile = profile.copy(
                leadScore = newScore,
                leadTier = newTier,
                updatedAt = System.currentTimeMillis()
            )
            
            aiAgentRepo.saveUserProfile(updatedProfile)
            
            val improvement = leadScorer.calculateScoreImprovement(oldScore, newScore)
            if (improvement != 0) {
                android.util.Log.d("LeadScoringHandler", "📊 Score: $oldScore → $newScore (${if (improvement > 0) "+" else ""}$improvement)")
            }
            
            return HandlerResult(success = true)
            
        } catch (e: Exception) {
            android.util.Log.e("LeadScoringHandler", "❌ Error: ${e.message}")
            return HandlerResult(success = false)
        }
    }
}
