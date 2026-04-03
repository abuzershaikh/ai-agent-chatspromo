package com.message.bulksend.autorespond.aireply.chatspromo

import android.content.Context
import android.util.Log
import com.message.bulksend.BuildConfig
import com.message.bulksend.autorespond.aireply.AIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChatsPromoGeminiService(private val context: Context) {

    companion object {
        private const val TAG = "ChatsPromoGemini"
        private const val STATUS_PATH = "/chatspromo/status"
        private const val GENERATE_PATH = "/chatspromo/generate-content"
    }

    data class WorkerStatus(
        val isReady: Boolean,
        val model: String,
        val message: String
    )

    private data class JsonHttpResponse(val code: Int, val body: String)

    fun hasWorkerEndpoint(): Boolean {
        return BuildConfig.CHATSPROMO_WORKER_URL.trim().isNotBlank()
    }

    suspend fun fetchWorkerStatus(): WorkerStatus = withContext(Dispatchers.IO) {
        if (!hasWorkerEndpoint()) {
            return@withContext WorkerStatus(
                isReady = false,
                model = "Server not connected",
                message = "CHATSPROMO_WORKER_URL missing"
            )
        }

        return@withContext runCatching {
            val response = requestWorker("GET", STATUS_PATH, null)
            if (response.code !in 200..299) {
                return@runCatching WorkerStatus(
                    isReady = false,
                    model = "Unavailable",
                    message = "Worker error ${response.code}"
                )
            }

            val json = JSONObject(response.body)
            WorkerStatus(
                isReady = json.optBoolean("success", false),
                model = json.optString("model").ifBlank { "Server Managed Gemini" },
                message = json.optString("message").ifBlank { "Ready" }
            )
        }.getOrElse {
            WorkerStatus(
                isReady = false,
                model = "Unavailable",
                message = it.message ?: "Worker request failed"
            )
        }
    }

    suspend fun generateReply(config: AIConfig, prompt: String): String = withContext(Dispatchers.IO) {
        if (!hasWorkerEndpoint()) {
            return@withContext "ChatsPromo AI server not configured. Add CHATSPROMO_WORKER_URL first."
        }

        val requestBody = buildStandardGeminiRequest(config, prompt)
        val response = proxyGenerateContent(config.model, requestBody)
        if (response.code != 200) {
            Log.e(TAG, "Worker Gemini error ${response.code}: ${response.body}")
            return@withContext "Error ${response.code}: ${response.body.ifBlank { "ChatsPromo worker failed" }}"
        }

        return@withContext parseGeminiTextResponse(response.body)
    }

    suspend fun callGeminiWithNativeTools(
        config: AIConfig,
        prompt: String,
        stepAllowlist: Set<String>?,
        maxTurns: Int,
        buildFunctionDeclarations: (Set<String>?) -> JSONArray,
        executeFunction: suspend (String, JSONObject) -> String
    ): String? = withContext(Dispatchers.IO) {
        if (!hasWorkerEndpoint()) return@withContext null

        val declarations = buildFunctionDeclarations(stepAllowlist)
        if (declarations.length() == 0) return@withContext null

        val contents = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        }

        val tools = JSONArray().put(JSONObject().put("functionDeclarations", declarations))
        var lastText = ""

        repeat(maxTurns.coerceAtLeast(1)) {
            val requestBody = JSONObject().apply {
                put("contents", contents)
                put("tools", tools)
                put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", config.temperature)
                        .put("maxOutputTokens", if (config.maxTokens < 1000) 1000 else config.maxTokens)
                )
            }

            val response = proxyGenerateContent(config.model, requestBody)
            if (response.code != 200) {
                Log.e(TAG, "Worker native Gemini error ${response.code}: ${response.body}")
                return@withContext null
            }

            val json = JSONObject(response.body)
            val candidateContent =
                json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?: return@withContext null

            val parts = candidateContent.optJSONArray("parts") ?: JSONArray()
            contents.put(
                JSONObject()
                    .put("role", "model")
                    .put("parts", parts)
            )

            val functionCalls = mutableListOf<JSONObject>()
            val textChunks = mutableListOf<String>()
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                val text = part.optString("text").trim()
                if (text.isNotBlank()) textChunks += text
                part.optJSONObject("functionCall")?.let { functionCalls += it }
            }

            if (textChunks.isNotEmpty()) {
                lastText = textChunks.joinToString("\n").trim()
            }

            if (functionCalls.isEmpty()) {
                return@withContext lastText.takeIf { it.isNotBlank() }
            }

            val responseParts = JSONArray()
            functionCalls.forEach { fnCall ->
                val fnName = fnCall.optString("name").trim()
                if (fnName.isBlank()) return@forEach
                val args = fnCall.optJSONObject("args") ?: JSONObject()
                val resultText = executeFunction(fnName, args)
                val resultObj =
                    runCatching { JSONObject(resultText) }
                        .getOrElse { JSONObject().put("result", resultText) }

                responseParts.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject()
                            .put("name", fnName)
                            .put("response", resultObj)
                    )
                )
            }

            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", responseParts)
            )
        }

        return@withContext lastText.takeIf { it.isNotBlank() }
    }

    private fun buildStandardGeminiRequest(config: AIConfig, prompt: String): JSONObject {
        return JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray().apply {
                                    put(JSONObject().put("text", prompt))
                                }
                            )
                        }
                    )
                }
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", config.temperature)
                    val baseTokens = if (config.maxTokens < 1000) 1000 else config.maxTokens
                    val totalTokens = if (config.enableThinking) baseTokens + 2000 else baseTokens
                    put("maxOutputTokens", totalTokens)
                    if (config.enableThinking) {
                        put(
                            "thinkingConfig",
                            JSONObject().put("includeThoughts", true)
                        )
                    }
                }
            )
            put(
                "safetySettings",
                JSONArray().apply {
                    listOf(
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT"
                    ).forEach { category ->
                        put(
                            JSONObject()
                                .put("category", category)
                                .put("threshold", "BLOCK_NONE")
                        )
                    }
                }
            )
        }
    }

    private fun parseGeminiTextResponse(rawBody: String): String {
        return try {
            val jsonResponse = JSONObject(rawBody)
            if (!jsonResponse.has("candidates")) {
                if (jsonResponse.has("promptFeedback")) {
                    val feedback = jsonResponse.getJSONObject("promptFeedback")
                    val blockReason = feedback.optString("blockReason", "Unknown")
                    return "Content blocked by Gemini safety filters: $blockReason"
                }
                return "Error: No candidates in response"
            }

            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() == 0) return "Error: Empty candidates array"

            val candidate = candidates.getJSONObject(0)
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                return "Response blocked by safety filters. Try rephrasing your message."
            }

            val content = candidate.optJSONObject("content")
                ?: return "Error: No content in response"
            val parts = content.optJSONArray("parts")
                ?: return "Error: No parts in content"
            if (parts.length() == 0) return "Error: Empty parts array"

            val part = parts.optJSONObject(0) ?: return "Error: Empty Gemini part"
            val text = part.optString("text").trim()
            if (text.isBlank()) return "Gemini returned empty text. Try again."
            text
        } catch (error: Exception) {
            Log.e(TAG, "Failed to parse worker Gemini response: ${error.message}", error)
            "Error calling ChatsPromo Gemini: ${error.message}"
        }
    }

    private fun proxyGenerateContent(rawModel: String, payload: JSONObject): JsonHttpResponse {
        val request = JSONObject().apply {
            val resolvedModel = resolveWorkerModel(rawModel)
            if (resolvedModel.isNotBlank()) {
                put("model", resolvedModel)
            }
            put("payload", payload)
            put("appPackage", context.packageName)
        }
        return requestWorker("POST", GENERATE_PATH, request)
    }

    private fun resolveWorkerModel(rawModel: String): String {
        val cleanModel = rawModel.trim()
        if (cleanModel.isBlank()) return ""
        if (cleanModel.equals("chatspromo-v1", ignoreCase = true)) return ""
        return cleanModel
    }

    private fun requestWorker(
        method: String,
        path: String,
        body: JSONObject?
    ): JsonHttpResponse {
        val baseUrl = BuildConfig.CHATSPROMO_WORKER_URL.trim().trimEnd('/')
        val fullUrl = if (path.startsWith("/")) "$baseUrl$path" else "$baseUrl/$path"
        val connection = URL(fullUrl).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val clientToken = BuildConfig.CHATSPROMO_WORKER_CLIENT_TOKEN.trim()
            if (clientToken.isNotBlank()) {
                connection.setRequestProperty("X-ChatsPromo-Client-Token", clientToken)
            }

            connection.connectTimeout = 30000
            connection.readTimeout = 90000

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }

            val code = connection.responseCode
            val responseBody =
                if (code in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText().orEmpty()
                }
            JsonHttpResponse(code = code, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }
}
