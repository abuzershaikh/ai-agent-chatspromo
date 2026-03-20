package com.message.bulksend.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiApiService {
    
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(GeminiConfig.BASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            // Set request method and headers
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-goog-api-key", GeminiConfig.API_KEY)
            connection.doOutput = true
            
            // Create request body
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }
            
            // Send request
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            
            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                // Parse response
                val jsonResponse = JSONObject(response)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
                return@withContext "Sorry, I couldn't generate a response."
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                return@withContext "Error: $errorResponse"
            }
        } catch (e: Exception) {
            return@withContext "Error: ${e.message}"
        }
    }
    
    suspend fun generateBusinessResponse(question: String): String {
        val businessPrompt = """
            You are a business expert AI assistant. Provide helpful, practical advice for the following business question.
            Keep your response concise, actionable, and professional. Focus on practical steps and strategies.
            
            Question: $question
            
            Please provide a helpful business response:
        """.trimIndent()
        
        return generateResponse(businessPrompt)
    }
    
    suspend fun generateAppUsageResponse(question: String): String {
        val appUsagePrompt = """
            You are a helpful assistant for the BulkSend app. Here are the complete features of the app:

            ## CORE FEATURES:
            
            1. **Bulk Messaging System**: Send messages to multiple contacts simultaneously with Excel/CSV import, message templates, scheduling, and progress tracking for WhatsApp and WhatsApp Business.
            
            2. **WhatsApp Selection System**: Smart detection and selection between WhatsApp and WhatsApp Business with user preference saving.
            
            3. **ChatAI Assistant**: AI-powered business assistant with Business AI (growth strategies, marketing tips) and App Usage AI (feature guidance).
            
            4. **Lead Manager**: Comprehensive lead tracking with status management (New, Contacted, Qualified, Converted, Lost), follow-up scheduling, reports, and analytics.
            
            5. **Contact Manager**: Advanced contact management with import from various sources, grouping, duplicate detection, and export functionality.
            
            6. **Auto Responder**: Automated message responses with keyword-based triggers, time scheduling, and custom templates.
            
            7. **Message Templates**: Pre-designed templates with categories (Business, Personal, Marketing), variable placeholders, and sharing capabilities.
            
            8. **Notes System**: Rich text notes with categories, tags, search functionality, and sharing.
            
            9. **WhatsApp Data Extraction**: Extract chat history, contacts, and organize media files with data export.
            
            10. **Analytics Dashboard**: Comprehensive analytics with message delivery statistics, response rates, lead conversion metrics, and usage analytics.
            
            11. **Overlay System**: Floating overlay for quick access to messaging, contact shortcuts, and templates.
            
            12. **Subscription Plans**: Premium features with multiple tiers, payment integration, and usage tracking.
            
            13. **Data Synchronization**: Cloud sync and backup with cross-device synchronization and conflict resolution.
            
            14. **Authentication System**: User registration/login with password recovery, biometric authentication, and session management.
            
            15. **Support System**: Customer support with FAQ, ticket system, live chat, and video tutorials.
            
            16. **Tutorial System**: Interactive app tutorials with step-by-step guides and progress tracking.

            ## HOW TO USE KEY FEATURES:

            **Bulk Messaging**: Go to 'Send Message' → Create/select contact group → Write message/select template → Attach media → Click 'Launch Campaign'. Enable accessibility service for automatic sending.

            **Contact Groups**: Tap 'Campaign Contact List' → Click '+' → Import from phone/CSV/manual entry → Name and save group.

            **Templates**: Go to 'Manage Templates' → Create new or use existing → Add text, media, placeholders like #name# → Save → Select when creating campaigns.

            **Campaign Tracking**: Go to 'Reports' → View campaigns → Check status (Pending/Sent/Failed) → See analytics → Resume paused campaigns.

            **Lead Management**: Access Lead Manager → Add leads → Set status → Schedule follow-ups → Track conversion → Generate reports.

            User question: $question
            
            Please provide helpful, detailed guidance about using the BulkSend app features. Be specific and actionable:
        """.trimIndent()
        
        return generateResponse(appUsagePrompt)
    }
}