package com.message.bulksend.aiagent.tools.ownerassist

import android.content.Context
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import com.message.bulksend.autorespond.aireply.AIProvider

class OwnerAssistManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val reverseAIManager = ReverseAIManager(appContext)
    private val sheetOperationEngine = OwnerAssistSheetOperationEngine(appContext)

    fun isAuthorizedOwner(senderPhone: String): Boolean {
        if (!reverseAIManager.isReverseAIEnabled) return false

        val ownerDigits = normalizeDigits(reverseAIManager.ownerPhoneNumber)
        val senderDigits = normalizeDigits(senderPhone)
        if (ownerDigits.isBlank() || senderDigits.isBlank()) return false

        return senderDigits == ownerDigits ||
            senderDigits.endsWith(ownerDigits) ||
            ownerDigits.endsWith(senderDigits)
    }

    suspend fun processOwnerInstruction(
        instruction: String,
        provider: AIProvider = AIProvider.GEMINI
    ): String {
        sheetOperationEngine.maybeHandleSheetInstruction(instruction, provider)?.let { sheetResponse ->
            return sheetResponse
        }

        return reverseAIManager.processOwnerInstruction(instruction, provider)
    }

    private fun normalizeDigits(value: String?): String {
        return value.orEmpty().filter { it.isDigit() }
    }
}
