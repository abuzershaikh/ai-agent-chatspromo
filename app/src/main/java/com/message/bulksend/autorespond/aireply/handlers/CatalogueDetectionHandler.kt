package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.autorespond.ai.data.repo.AIAgentRepository
import com.message.bulksend.autorespond.ai.product.AIAgentProductManager
import com.message.bulksend.autorespond.ai.product.CatalogueSendingState
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.database.Product
import java.util.Locale

/**
 * Handler for detecting catalogue send requests in AI responses.
 *
 * Also supports optional autonomous intent-based catalogue triggering for
 * custom templates when enabled in settings.
 */
class CatalogueDetectionHandler(
    private val aiAgentRepo: AIAgentRepository,
    private val productManager: AIAgentProductManager
) : MessageHandler {

    private data class TargetResolution(
        val productId: Long? = null,
        val clarification: String? = null
    )

    override fun getPriority(): Int = 50

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        return try {
            val settingsManager = AIAgentSettingsManager(context)
            val lowerResponse = response.lowercase()
            val isCustomTemplate =
                settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)
            val customCatalogueEnabled =
                if (isCustomTemplate) {
                    settingsManager.customTemplateEnableAutonomousCatalogueSend
                } else {
                    true
                }

            val responsePatterns = listOf(
                "send.*catalogue",
                "send.*catalog",
                "sending.*catalogue",
                "sending.*catalog",
                "bhej.*catalogue",
                "bhej.*catalog",
                "i'll send.*catalogue",
                "i'll send.*catalog",
                "let me send.*catalogue",
                "let me send.*catalog",
                "catalogue bhej",
                "catalog bhej",
                "product bhej",
                "products bhej"
            )

            val responseWantsCatalogue =
                responsePatterns.any { pattern -> lowerResponse.contains(Regex(pattern)) }

            if (!customCatalogueEnabled) {
                // Custom catalogue switch is OFF: no send, no trigger text in runtime output.
                val cleaned = if (responseWantsCatalogue) stripSendingTrigger(response) else response
                return HandlerResult(success = true, modifiedResponse = cleaned.trim())
            }

            val shouldSendFromResponse = responseWantsCatalogue

            val autonomousEnabled =
                isCustomTemplate &&
                    settingsManager.customTemplateEnableAutonomousCatalogueSend
            val shouldSendFromIntent = autonomousEnabled && isCatalogueIntentMessage(message)

            if (!shouldSendFromResponse && !shouldSendFromIntent) {
                return HandlerResult(success = true)
            }

            android.util.Log.d(
                "CatalogueHandler",
                "Catalogue trigger detected (response=$shouldSendFromResponse, intent=$shouldSendFromIntent)"
            )

            val nameFromResponse = extractProductNameFromResponse(response)
            val nameFromMessage =
                if (shouldSendFromIntent) extractProductNameFromMessage(message) else null
            val targetName = nameFromResponse ?: nameFromMessage
            android.util.Log.d(
                "CatalogueHandler",
                "Target extraction: responseName='$nameFromResponse', messageName='$nameFromMessage', selected='$targetName'"
            )

            val resolution =
                resolveTargetProductId(
                    message = message,
                    productName = targetName,
                    shouldSendFromIntent = shouldSendFromIntent
                )
            val targetProductId = resolution.productId
            if (targetProductId == null) {
                android.util.Log.w("CatalogueHandler", "No exact product match for catalogue send")
                val cleaned = stripSendingTrigger(response)
                val clarification =
                    resolution.clarification
                        ?: "Kaunsa product bhejna hai? Product name likh dijiye."
                val merged =
                    when {
                        cleaned.isBlank() -> clarification
                        cleaned.contains(clarification, ignoreCase = true) -> cleaned
                        else -> "$cleaned\n\n$clarification"
                    }
                return HandlerResult(success = true, modifiedResponse = merged.trim())
            }

            CatalogueSendingState.getInstance().setPendingCatalogue(context, senderPhone, targetProductId)
            android.util.Log.d("CatalogueHandler", "Pending catalogue set for productId=$targetProductId")

            var modifiedResponse = stripSendingTrigger(response)

            if (shouldSendFromIntent && !shouldSendFromResponse) {
                val autoNote = "Main abhi aapko product catalogue bhej raha hoon."
                modifiedResponse =
                    if (modifiedResponse.isBlank()) {
                        autoNote
                    } else if (modifiedResponse.contains("catalogue", ignoreCase = true)) {
                        modifiedResponse
                    } else {
                        "$modifiedResponse\n\n$autoNote"
                    }
            }

            HandlerResult(success = true, modifiedResponse = modifiedResponse)
        } catch (e: Exception) {
            android.util.Log.e("CatalogueHandler", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private fun isCatalogueIntentMessage(message: String): Boolean {
        val lower = message.lowercase().trim()
        if (lower.isBlank()) return false

        val directSignals = listOf(
            "catalogue",
            "catalog",
            "product list",
            "show products",
            "show product",
            "send products",
            "send product",
            "share products",
            "products bhejo",
            "product bhejo",
            "catalogue bhejo",
            "catalog bhejo",
            "products dikhao",
            "product dikhao",
            "product dikhado"
        )
        if (directSignals.any { lower.contains(it) }) return true

        val actionWords = listOf("show", "send", "share", "bhej", "dikha", "dikh")
        return lower.contains("product") && actionWords.any { lower.contains(it) }
    }

    private fun extractProductNameFromResponse(response: String): String? {
        val line =
            response.lines().find {
                val lower = it.lowercase()
                lower.contains("sending") && lower.contains("catalogue")
            } ?: return null

        val pattern = Regex("sending\\s+([\\w\\s\\-]+?)\\s+catalogue", RegexOption.IGNORE_CASE)
        val raw = pattern.find(line)?.groupValues?.getOrNull(1)
        return sanitizeProductName(raw)
    }

    private fun extractProductNameFromMessage(message: String): String? {
        val patterns = listOf(
            Regex("send\\s+(.+?)\\s+catalog(?:ue)?", RegexOption.IGNORE_CASE),
            Regex("show\\s+(.+?)\\s+catalog(?:ue)?", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+ka\\s+catalog(?:ue)?", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+ki\\s+details?", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val raw = pattern.find(message)?.groupValues?.getOrNull(1)
            val clean = sanitizeProductName(raw)
            if (!clean.isNullOrBlank()) return clean
        }
        return null
    }

    private fun sanitizeProductName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned =
            raw.trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[^a-zA-Z0-9\\s\\-]"), "")
                .trim()
        if (cleaned.isBlank()) return null

        val blocked = setOf(
            "email",
            "with",
            "all",
            "media",
            "files",
            "the",
            "and",
            "or",
            "for",
            "to",
            "from",
            "product",
            "products",
            "catalog",
            "catalogue"
        )
        return if (blocked.contains(cleaned.lowercase())) null else cleaned
    }

    private fun stripSendingTrigger(response: String): String {
        val linePattern = Regex("\\bsending\\b.*\\bcatalog(?:ue)?\\b", RegexOption.IGNORE_CASE)
        return response
            .lines()
            .filterNot { linePattern.containsMatchIn(it) }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun buildAmbiguousProductPrompt(matches: List<Product>): String {
        val names = matches.take(4).joinToString(", ") { it.name }
        return "Mujhe multiple products mile: $names. Kaunsa product catalogue bhej du?"
    }

    private fun isGeneralCatalogueIntent(message: String): Boolean {
        val lower = message.lowercase().trim()
        if (lower.isBlank()) return true

        val broadSignals =
            listOf(
                "catalogue",
                "catalog",
                "product list",
                "all products",
                "show products",
                "send products",
                "products"
            )
        return broadSignals.any { lower == it || lower.contains(it) }
    }

    private suspend fun resolveTargetProductId(
        message: String,
        productName: String?,
        shouldSendFromIntent: Boolean
    ): TargetResolution {
        if (!productName.isNullOrBlank()) {
            val byName = productManager.getProductByName(productName)
            if (byName != null && byName.isVisible) return TargetResolution(productId = byName.id)

            val byNameMatches = aiAgentRepo.searchProducts(productName).filter { it.isVisible }
            return when {
                byNameMatches.size == 1 -> TargetResolution(productId = byNameMatches.first().id)
                byNameMatches.size > 1 ->
                    TargetResolution(clarification = buildAmbiguousProductPrompt(byNameMatches))
                else -> {
                    val tokenResolution = resolveByTokenSearch(productName)
                    if (tokenResolution != null) {
                        return tokenResolution
                    }
                    TargetResolution(
                        clarification =
                            "Mujhe \"$productName\" product nahi mila. Exact product name bhej dijiye."
                    )
                }
            }
        }

        if (shouldSendFromIntent && isGeneralCatalogueIntent(message)) {
            return TargetResolution(
                clarification =
                    "Kaunsa product bhejna hai? Product name likh dijiye, jaise \"iPhone 15\"."
            )
        }

        val searchMatches = aiAgentRepo.searchProducts(message).filter { it.isVisible }
        return when {
            searchMatches.size == 1 -> TargetResolution(productId = searchMatches.first().id)
            searchMatches.size > 1 ->
                TargetResolution(clarification = buildAmbiguousProductPrompt(searchMatches))
            else -> {
                val tokenResolution = resolveByTokenSearch(message)
                if (tokenResolution != null) {
                    tokenResolution
                } else {
                    TargetResolution(
                        clarification = "Exact product match nahi mila. Product ka exact naam bhej dijiye."
                    )
                }
            }
        }
    }

    private suspend fun resolveByTokenSearch(rawQuery: String): TargetResolution? {
        val tokens = extractProductIdentityTokens(rawQuery)
        if (tokens.isEmpty()) return null

        val visibleProducts = aiAgentRepo.getAllVisibleProducts()
        if (visibleProducts.isEmpty()) return null

        val scored =
            visibleProducts.map { product ->
                product to scoreProductTokenMatch(product, tokens)
            }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

        if (scored.isEmpty()) {
            android.util.Log.d(
                "CatalogueHandler",
                "Token search found no match. query='$rawQuery', tokens=$tokens"
            )
            return null
        }

        val topScore = scored.first().second
        val topCandidates = scored.filter { it.second == topScore }.map { it.first }
        val preview =
            scored.take(4).joinToString { "${it.first.name}:${it.second}" }
        android.util.Log.d(
            "CatalogueHandler",
            "Token search candidates. query='$rawQuery', tokens=$tokens, topScore=$topScore, scored=[$preview]"
        )

        return when {
            topCandidates.size == 1 -> TargetResolution(productId = topCandidates.first().id)
            else -> TargetResolution(clarification = buildAmbiguousProductPrompt(topCandidates))
        }
    }

    private fun extractProductIdentityTokens(raw: String): List<String> {
        val stopWords =
            setOf(
                "send",
                "show",
                "share",
                "bhejo",
                "bhej",
                "catalog",
                "catalogue",
                "product",
                "products",
                "details",
                "detail",
                "please",
                "plz",
                "with",
                "all",
                "media",
                "files",
                "ka",
                "ki",
                "ke",
                "me",
                "mein",
                "mujhe",
                "and",
                "or",
                "the",
                "for",
                "to",
                "price",
                "stock",
                "size",
                "colour",
                "color",
                "red",
                "blue",
                "green",
                "black",
                "white",
                "yellow",
                "pink",
                "orange",
                "brown",
                "grey",
                "gray",
                "xs",
                "s",
                "m",
                "l",
                "xl",
                "xxl",
                "xxxl"
            )

        return normalizeLookupText(raw)
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it in stopWords }
            .filter { token -> token.length >= 2 || token.any { it.isDigit() } }
            .distinct()
            .take(8)
    }

    private fun scoreProductTokenMatch(product: Product, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0

        val nameText = normalizeLookupText(product.name)
        val categoryText = normalizeLookupText(product.category)
        val descriptionText = normalizeLookupText(product.description)
        val nameWords = nameText.split(" ").filter { it.isNotBlank() }.toSet()

        var score = 0
        tokens.forEach { token ->
            if (token in nameWords) {
                score += 11
            } else if (nameText.contains(token)) {
                score += 7
            }
            if (categoryText.contains(token)) {
                score += 3
            }
            if (descriptionText.contains(token)) {
                score += 2
            }
        }

        val coveredNameTokens = tokens.count { it in nameWords || nameText.contains(it) }
        if (coveredNameTokens == tokens.size && coveredNameTokens > 0) {
            score += 8
        }

        return score
    }

    private fun normalizeLookupText(raw: String): String {
        return raw
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
