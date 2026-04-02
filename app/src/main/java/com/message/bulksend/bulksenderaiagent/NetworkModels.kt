package com.message.bulksend.bulksenderaiagent

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AiAgentNetworkService {
    @POST(BulksenderAiAgentWorkerConfig.CHAT_ENDPOINT)
    suspend fun getChatResponse(@Body request: ChatRequest): Response<ChatResponse>
}

data class ChatRequest(
    val message: String,
    val history: List<ChatHistoryItem>,
    val plan: String
)

data class ChatHistoryItem(
    val role: String, // "user" or "model"
    val parts: List<ChatPart>
)

data class ChatPart(
    val text: String
)

data class ChatResponse(
    val reply: String,
    val action: String = "NONE",
    val context: String? = null
)
