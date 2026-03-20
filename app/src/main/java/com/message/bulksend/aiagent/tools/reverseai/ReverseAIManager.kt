package com.message.bulksend.aiagent.tools.reverseai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.message.bulksend.autorespond.aireply.AIProvider
import com.message.bulksend.autorespond.aireply.AIService
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.repository.LeadRepository
import com.message.bulksend.reminders.GlobalReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class ReverseAIManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("ReverseAIPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val contactExportTool = ReverseAIContactExportTool(context)

    var isReverseAIEnabled: Boolean
        get() = prefs.getBoolean("is_reverse_ai_enabled", false)
        set(value) = prefs.edit().putBoolean("is_reverse_ai_enabled", value).apply()

    var ownerPhoneNumber: String
        get() = prefs.getString("owner_phone_number", "") ?: ""
        set(value) {
            val cleaned = normalizeOwnerPhone(value)
            prefs.edit().putString("owner_phone_number", cleaned).apply()
        }

    var requireReminderTriggerKeyword: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_REMINDER_TRIGGER_KEYWORD, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_REMINDER_TRIGGER_KEYWORD, value).apply()

    var reminderTriggerKeywords: String
        get() = prefs.getString(KEY_REMINDER_TRIGGER_KEYWORDS, DEFAULT_REMINDER_TRIGGER_KEYWORDS)
            ?: DEFAULT_REMINDER_TRIGGER_KEYWORDS
        set(value) = prefs.edit().putString(KEY_REMINDER_TRIGGER_KEYWORDS, value).apply()

    fun isMessageFromOwner(senderPhone: String): Boolean {
        if (!isReverseAIEnabled) return false
        val ownerPhone = ownerPhoneNumber
        if (ownerPhone.isEmpty()) return false
        
        // Match numbers, ignoring non-digits
        val cleanOwner = ownerPhone.replace(Regex("[^0-9]"), "")
        val cleanSender = senderPhone.replace(Regex("[^0-9]"), "")
        
        // Exact match or partial match (without country code)
        return cleanSender == cleanOwner || cleanSender.endsWith(cleanOwner) || cleanOwner.endsWith(cleanSender)
    }

    suspend fun processOwnerInstruction(instruction: String, provider: AIProvider = AIProvider.GEMINI): String = withContext(Dispatchers.IO) {
        try {
            Log.d("ReverseAI", "Processing instruction: $instruction")

            // 1. Gather Lead Context
            val leadRepo = LeadRepository(context)
            val leads = leadRepo.getAllLeadsList()
            val knownContacts = leads.map { KnownContact(it.name.trim(), normalizePhone(it.phoneNumber)) }

            // If owner previously asked for customer export and now shared only format.
            processPendingContactExportWithProvidedFormat(instruction)?.let { followUpMessage ->
                return@withContext followUpMessage
            }

            // Owner command: export customer list (from sheet name + number data).
            if (contactExportTool.isCustomerListExportInstruction(instruction)) {
                return@withContext handleContactExportInstruction(instruction)
            }

            // If previous reminder is pending for missing details, consume current message.
            // This is checked AFTER export-intent handling so reminder state doesn't hijack tools.
            processPendingReminderWithProvidedDetails(instruction, knownContacts)?.let { followUpMessage ->
                return@withContext followUpMessage
            }

            if (!hasReminderSchedulingIntent(instruction)) {
                return@withContext buildReminderIntentHelpMessage()
            }
            
            // Create a small map of names to phone numbers to help AI match
            val leadContextList = knownContacts.map { "${it.name} (${it.phone})" }
            val leadContextStr = if (leadContextList.isNotEmpty()) {
                "Known Contacts Data: ${leadContextList.joinToString(", ")}\n"
            } else {
                "No known contacts data available.\n"
            }

            // 2. Build the precise AI Prompt
            val aiPrompt = """
                You are a 'Reverse AI Assistant' for a busy business owner.
                The owner sent this voice-transcribed instruction: "$instruction"
                
                Your job is to parse this instruction to schedule a follow-up.
                
                $leadContextStr
                
                Rules:
                1. Identify the 'Name' of the lead mentioned.
                2. If the 'Name' matches one in the Known Contacts Data, use that exact matching Phone Number. If not found, output an empty string "" for phone.
                3. Extract the 'Interest / Context / Instruction' (e.g., "Interested in 2BHK, send brochure").
                4. Extract the intended follow-up Date strictly in "YYYY-MM-DD" format. If they say "kal" or "tomorrow", calculate it based on today's date context (Today is ${java.time.LocalDate.now()}).
                5. Extract the intended follow-up Time strictly in "HH:mm" (24-hour format). E.g., "5 baje sham" -> "17:00". "11am" -> "11:00".
                6. Extract exact outgoing message text that must be sent at reminder time. If not present, keep it empty.
                
                Output ONLY a JSON object in this exact structure without any markdown or code blocks:
                {
                  "name": "extracted name",
                  "phone": "matched phone or empty",
                  "context": "extracted interest and follow up instructions",
                  "date": "YYYY-MM-DD",
                  "time": "HH:mm",
                  "outgoing_message": "exact message to send or empty"
                }
            """.trimIndent()

            // 3. Call AI Service
            val aiService = AIService(context)
            // Reusing generateReply for now, but asking it to act as parser
            val jsonResponseRaw = aiService.generateReply(
                provider = provider,
                message = aiPrompt,
                senderName = "System",
                senderPhone = "Internal"
            )

            // 4. Parse JSON robustly with fallback when model returns prose/markdown
            Log.d("ReverseAI", "Raw parser response: $jsonResponseRaw")
            val parsedResult = parseInstructionFromAi(
                rawAiResponse = jsonResponseRaw,
                originalInstruction = instruction,
                knownContacts = knownContacts,
                leads = leads
            )

            // 5. Resolve final reminder fields
            val matchedContact = parsedResult.name
                ?.let { findBestContactMatch(it, knownContacts) }
                ?: findBestContactMatch(instruction, knownContacts)

            val resolvedName = sanitizeName(parsedResult.name)
                .ifBlank { matchedContact?.name.orEmpty() }
                .ifBlank { extractNameFromOwnerInput(instruction, knownContacts) }

            val resolvedPhone = normalizePhone(parsedResult.phone)
                .ifBlank { matchedContact?.phone.orEmpty() }
                .ifBlank { extractPhoneFromText(instruction) }

            val finalDate = resolveDate(parsedResult.date, instruction).format(DATE_FORMATTER)
            val finalTime = resolveTime(parsedResult.time, instruction).format(TIME_FORMATTER)
            val finalContext = parsedResult.context?.trim().orEmpty().ifBlank { instruction.trim() }
            val finalOutgoingMessage = parsedResult.outgoingMessage
                ?.trim()
                .orEmpty()
                .ifBlank { extractReminderMessageFromText(instruction) }

            val needsName = resolvedName.isBlank()
            val needsPhone = resolvedPhone.isBlank()
            val needsMessage = finalOutgoingMessage.isBlank()

            if (needsName || needsPhone || needsMessage) {
                savePendingReminder(
                    PendingReminder(
                        name = resolvedName,
                        phone = resolvedPhone,
                        context = finalContext,
                        outgoingMessage = finalOutgoingMessage,
                        date = finalDate,
                        time = finalTime,
                        originalInstruction = instruction.trim(),
                        createdAt = System.currentTimeMillis(),
                        requiresName = needsName,
                        requiresPhone = needsPhone,
                        requiresMessage = needsMessage
                    )
                )
                return@withContext buildReminderDraftMessage(
                    name = resolvedName,
                    phone = resolvedPhone,
                    date = finalDate,
                    time = finalTime,
                    context = finalContext,
                    outgoingMessage = finalOutgoingMessage,
                    needsName = needsName,
                    needsPhone = needsPhone,
                    needsMessage = needsMessage
                )
            }

            // 6. Schedule via GlobalReminderManager
            val globalReminderManager = GlobalReminderManager(context)
            globalReminderManager.addReminder(
                phone = resolvedPhone,
                name = resolvedName,
                date = finalDate, // YYYY-MM-DD
                time = finalTime, // HH:mm
                prompt = "Follow up regarding: $finalContext",
                ownerMessage = finalOutgoingMessage
            )

            clearPendingReminder()
            return@withContext buildReminderConfirmationMessage(
                name = resolvedName,
                phone = resolvedPhone,
                date = finalDate,
                time = finalTime,
                context = finalContext,
                outgoingMessage = finalOutgoingMessage
            )

        } catch (e: Exception) {
            Log.e("ReverseAI", "Error processing owner instruction: ${e.message}", e)
            return@withContext "Could not process the instruction. Try: 'Set a follow-up for Asif tomorrow at 5 PM.'"
        }
    }

    private suspend fun processPendingReminderWithProvidedDetails(
        instruction: String,
        knownContacts: List<KnownContact>
    ): String? {
        val pending = getPendingReminder() ?: return null
        val normalizedInstruction = instruction.lowercase(Locale.getDefault())

        val cancelPhrases = listOf(
            "cancel reminder",
            "reminder cancel",
            "cancel follow up",
            "follow up cancel",
            "follow-up cancel",
            "reminder nahi",
            "nahi reminder",
            "mat lagao"
        )
        if (cancelPhrases.any { normalizedInstruction.contains(it) }) {
            clearPendingReminder()
            return "Pending reminder draft canceled."
        }

        if (!isLikelyReminderPendingResponse(instruction, pending)) {
            return null
        }

        val incomingName = extractNameFromOwnerInput(instruction, knownContacts)
        val incomingPhone = extractPhoneFromText(instruction)
        val incomingMessage = extractReminderMessageFromText(instruction)

        val mergedName = sanitizeName(pending.name).ifBlank { incomingName }
        val mergedPhone = normalizePhone(pending.phone).ifBlank { incomingPhone }
        val mergedMessage = pending.outgoingMessage.trim().ifBlank { incomingMessage }

        val needsName = pending.requiresName && mergedName.isBlank()
        val needsPhone = pending.requiresPhone && mergedPhone.isBlank()
        val needsMessage = pending.requiresMessage && mergedMessage.isBlank()

        if (needsName || needsPhone || needsMessage) {
            savePendingReminder(
                pending.copy(
                    name = mergedName,
                    phone = mergedPhone,
                    outgoingMessage = mergedMessage,
                    createdAt = System.currentTimeMillis()
                )
            )

            return buildReminderDraftMessage(
                name = mergedName,
                phone = mergedPhone,
                date = resolveDate(pending.date, pending.originalInstruction).format(DATE_FORMATTER),
                time = resolveTime(pending.time, pending.originalInstruction).format(TIME_FORMATTER),
                context = pending.context.ifBlank { pending.originalInstruction },
                outgoingMessage = mergedMessage,
                needsName = needsName,
                needsPhone = needsPhone,
                needsMessage = needsMessage
            )
        }

        val finalName = mergedName
        val finalDate = resolveDate(pending.date, pending.originalInstruction).format(DATE_FORMATTER)
        val finalTime = resolveTime(pending.time, pending.originalInstruction).format(TIME_FORMATTER)
        val finalMessage = mergedMessage
        val finalContext = pending.context.trim().ifBlank {
            pending.originalInstruction.ifBlank { "General follow-up" }
        }

        val globalReminderManager = GlobalReminderManager(context)
        globalReminderManager.addReminder(
            phone = mergedPhone,
            name = finalName,
            date = finalDate,
            time = finalTime,
            prompt = "Follow up regarding: $finalContext",
            ownerMessage = finalMessage
        )
        clearPendingReminder()

        return buildReminderConfirmationMessage(
            name = finalName,
            phone = mergedPhone,
            date = finalDate,
            time = finalTime,
            context = finalContext,
            outgoingMessage = finalMessage
        )
    }

    private fun isLikelyReminderPendingResponse(
        instruction: String,
        pending: PendingReminder
    ): Boolean {
        val text = instruction.trim()
        if (text.isBlank()) return false

        val lower = text.lowercase(Locale.getDefault())
        if (contactExportTool.isCustomerListExportInstruction(text)) return false

        val formatOnly = contactExportTool.detectFormatFromInstruction(text) != null &&
            !lower.contains("reminder") &&
            !lower.contains("follow up") &&
            !lower.contains("follow-up")
        if (formatOnly) return false

        if (extractPhoneFromText(text).isNotBlank()) return true

        if (pending.requiresPhone) {
            if (Regex("(?i)\\b(phone|mobile|number|country\\s*code)\\b").containsMatchIn(text)) return true
        }
        if (pending.requiresMessage) {
            if (extractReminderMessageFromText(text).isNotBlank()) return true
            if (Regex("(?i)\\b(message|msg|text|bolna|bolo|reply|bhejna|send)\\b").containsMatchIn(text)) return true
        }

        if (pending.requiresName) {
            if (Regex("(?i)\\b(name|naam)\\b").containsMatchIn(text)) return true

            val candidateTokens = lower
                .replace(Regex("[^\\p{L}\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
            val blocked = setOf(
                "customer", "customers", "contact", "contacts", "lead", "list", "sheet",
                "csv", "vcf", "excel", "xlsx", "file", "export", "send", "bhej", "share",
                "reminder", "follow", "up"
            )
            if (candidateTokens.size in 1..3 && candidateTokens.none { blocked.contains(it) }) {
                return true
            }
        }

        if (Regex("(?i)\\b(yes|correct|right|haan|han|ha|no|nahi)\\b").containsMatchIn(text)) return true
        if (Regex("(?i)\\b(today|tomorrow|kal|aaj|monday|tuesday|wednesday|thursday|friday|saturday|sunday|subah|sham|shaam|raat|morning|evening|night)\\b").containsMatchIn(text)) return true
        if (Regex("(?i)\\b\\d{1,2}(:\\d{2})?\\s*(am|pm|baje)?\\b").containsMatchIn(text)) return true
        if (lower.contains("reminder") || lower.contains("follow up") || lower.contains("follow-up")) return true

        return false
    }

    private suspend fun processPendingContactExportWithProvidedFormat(
        instruction: String
    ): String? {
        val pending = getPendingContactExportRequest() ?: return null
        val normalized = instruction.lowercase(Locale.getDefault())

        if (listOf("cancel", "stop", "rehne do", "chhodo", "mat bhejo").any { normalized.contains(it) }) {
            clearPendingContactExportRequest()
            return "Customer list export request canceled."
        }

        val format = contactExportTool.detectFormatFromInstruction(instruction)

        if (format == null) {
            return """
                Customer list export is pending.

                Choose a format:
                1) Excel (.xlsx)
                2) CSV (.csv)
                3) VCF (.vcf)

                Reply: excel / csv / vcf
            """.trimIndent()
        }

        clearPendingContactExportRequest()
        return executeContactExport(format, pending)
    }

    private suspend fun handleContactExportInstruction(instruction: String): String {
        val format = contactExportTool.detectFormatFromInstruction(instruction)
        if (format == null) {
            savePendingContactExportRequest(instruction)
            return """
                Customer list export request received.

                Which format do you want?
                1) Excel (.xlsx)
                2) CSV (.csv)
                3) VCF (.vcf)

                Reply: excel / csv / vcf
            """.trimIndent()
        }

        clearPendingContactExportRequest()
        return executeContactExport(format, instruction)
    }

    private suspend fun executeContactExport(
        format: ReverseAIContactExportTool.ExportFormat,
        sourceInstruction: String
    ): String {
        val ownerPhone = ownerPhoneNumber.trim()
        if (ownerPhone.isBlank()) {
            return """
                Export failed.
                Owner number is not configured.
                Set the owner phone number in Reverse AI settings.
            """.trimIndent()
        }

        val result = contactExportTool.exportAndQueueToOwner(ownerPhone, format)
        return if (result.success) {
            """
                Customer List Export Ready

                Format : ${result.format.displayName}
                Contacts: ${result.contactCount}
                File    : ${result.fileName}

                Status  : File generated and queued in owner chat.
                Source  : ${sourceInstruction.trim()}
            """.trimIndent()
        } else {
            """
                Customer list export failed.

                Reason : ${result.message}
                Format : ${result.format.displayName}
            """.trimIndent()
        }
    }

    private fun getPendingContactExportRequest(): String? {
        val request = prefs.getString(KEY_PENDING_EXPORT_REQUEST, "")?.trim().orEmpty()
        if (request.isBlank()) return null

        val createdAt = prefs.getLong(KEY_PENDING_EXPORT_CREATED_AT, 0L)
        if (createdAt > 0L && (System.currentTimeMillis() - createdAt) > PENDING_REMINDER_TTL_MS) {
            clearPendingContactExportRequest()
            return null
        }

        return request
    }

    private fun savePendingContactExportRequest(request: String) {
        prefs.edit()
            .putString(KEY_PENDING_EXPORT_REQUEST, request.trim())
            .putLong(KEY_PENDING_EXPORT_CREATED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearPendingContactExportRequest() {
        prefs.edit()
            .remove(KEY_PENDING_EXPORT_REQUEST)
            .remove(KEY_PENDING_EXPORT_CREATED_AT)
            .apply()
    }

    private fun parseInstructionFromAi(
        rawAiResponse: String,
        originalInstruction: String,
        knownContacts: List<KnownContact>,
        leads: List<Lead>
    ): AIParsedInstruction {
        val candidates = linkedSetOf<String>()
        val fenceStripped = stripCodeFences(rawAiResponse)

        if (fenceStripped.isNotBlank()) candidates += fenceStripped
        extractJsonObject(fenceStripped)?.let { candidates += it }
        extractJsonObject(rawAiResponse)?.let { candidates += it }
        decodeQuotedJson(fenceStripped)?.let { quoted ->
            if (quoted.isNotBlank()) {
                candidates += quoted
                extractJsonObject(quoted)?.let { candidates += it }
            }
        }

        for (candidate in candidates) {
            val parsed = tryParseInstruction(candidate)
            if (parsed != null) {
                Log.d("ReverseAI", "Owner instruction parsed successfully.")
                return parsed
            }
        }

        Log.w("ReverseAI", "AI parser response invalid. Falling back to heuristic parser.")
        return buildHeuristicInstruction(originalInstruction, knownContacts, leads)
    }

    private fun tryParseInstruction(candidate: String): AIParsedInstruction? {
        val cleaned = candidate.trim()
        if (cleaned.isBlank()) return null

        return try {
            val parsed = gson.fromJson(cleaned, AIParsedInstruction::class.java)
            if (hasAnyField(parsed)) parsed else null
        } catch (e: Exception) {
            Log.w("ReverseAI", "JSON parse candidate failed: ${e.message}")
            null
        }
    }

    private fun hasAnyField(parsed: AIParsedInstruction?): Boolean {
        if (parsed == null) return false
        return !parsed.name.isNullOrBlank() ||
            !parsed.phone.isNullOrBlank() ||
            !parsed.context.isNullOrBlank() ||
            !parsed.date.isNullOrBlank() ||
            !parsed.time.isNullOrBlank() ||
            !parsed.outgoingMessage.isNullOrBlank()
    }

    private fun buildHeuristicInstruction(
        instruction: String,
        knownContacts: List<KnownContact>,
        leads: List<Lead>
    ): AIParsedInstruction {
        val matchedContact = findBestContactMatch(instruction, knownContacts)
        val resolvedName = matchedContact?.name ?: inferNameFromLeads(instruction, leads)
        val resolvedPhone = matchedContact?.phone ?: extractPhoneFromText(instruction)
        val resolvedDate = extractDateFromText(instruction)?.format(DATE_FORMATTER)
        val resolvedTime = extractTimeFromText(instruction)?.format(TIME_FORMATTER)
        val outgoingMessage = extractReminderMessageFromText(instruction)

        return AIParsedInstruction(
            name = resolvedName,
            phone = resolvedPhone,
            context = instruction.trim(),
            date = resolvedDate,
            time = resolvedTime,
            outgoingMessage = outgoingMessage
        )
    }

    private fun sanitizeName(value: String?): String {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isBlank()) return ""
        if (isTransientProgressLikeText(cleaned)) return ""

        return when (cleaned.lowercase(Locale.getDefault())) {
            "unknown", "unknown lead", "na", "n/a", "none", "null", "\"\"" -> ""
            else -> cleaned
        }
    }

    private fun normalizePhone(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""

        val lower = raw.lowercase(Locale.getDefault())
        if (lower == "unknown" || lower == "unknown phone" || lower == "na" || lower == "n/a" || lower == "none" || lower == "null" || lower == "\"\"") {
            return ""
        }

        val hasPlus = raw.startsWith("+")
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return ""

        return if (hasPlus) "+$digits" else digits
    }

    private fun stripCodeFences(value: String): String {
        var cleaned = value.trim()
        cleaned = cleaned.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s*```$"), "")
        return cleaned.trim()
    }

    private fun extractJsonObject(value: String): String? {
        val start = value.indexOf('{')
        if (start < 0) return null

        var depth = 0
        for (index in start until value.length) {
            when (value[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return value.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun decodeQuotedJson(value: String): String? {
        val text = value.trim()
        if (!text.startsWith("\"") || !text.endsWith("\"")) return null
        return try {
            gson.fromJson(text, String::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun findBestContactMatch(text: String, contacts: List<KnownContact>): KnownContact? {
        val normalizedText = normalizeForMatch(text)
        if (normalizedText.isBlank()) return null

        return contacts
            .asSequence()
            .filter { it.name.isNotBlank() }
            .map { it to scoreContactMatch(normalizedText, it.name) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun scoreContactMatch(normalizedText: String, candidateName: String): Int {
        val normalizedName = normalizeForMatch(candidateName)
        if (normalizedName.isBlank()) return 0
        if (normalizedText.contains(normalizedName)) return 100 + normalizedName.length

        val tokens = normalizedName.split(" ").filter { it.length > 1 }
        if (tokens.isEmpty()) return 0

        var score = 0
        tokens.forEach { token ->
            val exactTokenPattern = Regex("\\b${Regex.escape(token)}\\b")
            score += when {
                exactTokenPattern.containsMatchIn(normalizedText) -> 20
                normalizedText.contains(token) -> 8
                else -> 0
            }
        }
        return if (score >= 20) score else 0
    }

    private fun normalizeForMatch(value: String): String {
        return value
            .lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun inferNameFromLeads(instruction: String, leads: List<Lead>): String? {
        if (leads.isEmpty()) return null
        val normalizedInstruction = normalizeForMatch(instruction)
        return leads
            .map { it.name }
            .firstOrNull { name -> normalizeForMatch(name).let { it.isNotBlank() && normalizedInstruction.contains(it) } }
    }

    private fun resolveDate(parsedDate: String?, instruction: String): LocalDate {
        return parseDateValue(parsedDate)
            ?: extractDateFromText(instruction)
            ?: LocalDate.now().plusDays(1)
    }

    private fun resolveTime(parsedTime: String?, instruction: String): LocalTime {
        return parseTimeValue(parsedTime)
            ?: extractTimeFromText(instruction)
            ?: LocalTime.of(10, 0)
    }

    private fun extractDateFromText(text: String): LocalDate? = parseDateValue(text)

    private fun parseDateValue(rawValue: String?): LocalDate? {
        val value = rawValue?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        if (value.isBlank()) return null

        if (Regex("\\b(tomorrow|kal)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            return LocalDate.now().plusDays(1)
        }
        if (Regex("\\b(today|aaj)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            return LocalDate.now()
        }

        try {
            return LocalDate.parse(value, DATE_FORMATTER)
        } catch (_: DateTimeParseException) {
            // Continue with regex parsing
        }

        Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b").find(value)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val yearRaw = match.groupValues[3].toIntOrNull() ?: return@let
            val year = if (yearRaw < 100) 2000 + yearRaw else yearRaw
            buildDate(day, month, year)?.let { return it }
        }

        Regex("\\b(\\d{1,2})[/-](\\d{1,2})\\b").find(value)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val currentYear = LocalDate.now().year
            val candidate = buildDate(day, month, currentYear)
            if (candidate != null) {
                return if (candidate.isBefore(LocalDate.now().minusDays(1))) candidate.plusYears(1) else candidate
            }
        }

        parseDayOfWeek(value)?.let { dayOfWeek ->
            return LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek))
        }

        return null
    }

    private fun buildDate(day: Int, month: Int, year: Int): LocalDate? {
        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDayOfWeek(value: String): DayOfWeek? {
        return when {
            Regex("\\b(monday|somvar)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.MONDAY
            Regex("\\b(tuesday|mangalvar|mangal)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.TUESDAY
            Regex("\\b(wednesday|budhvar|budh)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.WEDNESDAY
            Regex("\\b(thursday|guruvar|guruwar|brihaspativar)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.THURSDAY
            Regex("\\b(friday|shukravar|shukrvar)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.FRIDAY
            Regex("\\b(saturday|shanivar|shanivaar)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.SATURDAY
            Regex("\\b(sunday|ravivar|ravivaar)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun extractTimeFromText(text: String): LocalTime? = parseTimeValue(text)

    private fun parseTimeValue(rawValue: String?): LocalTime? {
        val value = rawValue?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        if (value.isBlank()) return null

        Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(value)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return LocalTime.of(hour, minute)
        }

        Regex("\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b", RegexOption.IGNORE_CASE).find(value)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val period = match.groupValues[3]
            return resolveHourByPeriod(hour, minute, period)
        }

        Regex("\\b(\\d{1,2})(?::([0-5]\\d))?\\s*(baje|bajey|o'?clock)?\\s*(subah|morning|dopahar|afternoon|sham|shaam|evening|raat|night)?\\b", RegexOption.IGNORE_CASE).find(value)?.let { match ->
            val marker = match.groupValues[3]
            val dayPeriod = match.groupValues[4]
            if (marker.isNotBlank() || dayPeriod.isNotBlank()) {
                val hour = match.groupValues[1].toIntOrNull() ?: return@let
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                return resolveHourByPeriod(hour, minute, dayPeriod)
            }
        }

        Regex("\\bat\\s*(\\d{1,2})(?::([0-5]\\d))?\\b", RegexOption.IGNORE_CASE).find(value)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        }

        return null
    }

    private fun resolveHourByPeriod(rawHour: Int, minute: Int, periodToken: String): LocalTime? {
        if (minute !in 0..59) return null
        if (rawHour !in 0..23) return null

        var hour = rawHour
        val token = periodToken
            .lowercase(Locale.getDefault())
            .replace(".", "")
            .trim()

        if (token.contains("pm") || token.contains("evening") || token.contains("sham") || token.contains("shaam") || token.contains("night") || token.contains("raat") || token.contains("afternoon") || token.contains("dopahar")) {
            if (hour in 1..11) hour += 12
        } else if (token.contains("am") || token.contains("morning") || token.contains("subah")) {
            if (hour == 12) hour = 0
        }

        return if (hour in 0..23) LocalTime.of(hour, minute) else null
    }

    private fun extractPhoneFromText(text: String): String {
        val match = Regex("(\\+?\\d[\\d\\s()\\-]{7,}\\d)").find(text)
        return normalizePhone(match?.groupValues?.get(1))
    }

    private fun extractNameFromOwnerInput(
        text: String,
        knownContacts: List<KnownContact>
    ): String {
        if (isTransientProgressLikeText(text)) return ""

        val matchedContact = findBestContactMatch(text, knownContacts)
        if (matchedContact != null) return matchedContact.name

        val explicitPattern = Regex(
            "(?i)\\b(?:name|naam)\\s*[:\\-]\\s*([\\p{L}][\\p{L}\\s]{1,40})"
        )
        explicitPattern.find(text)?.groupValues?.getOrNull(1)?.let { candidate ->
            val sanitized = sanitizeNameCandidate(candidate)
            if (sanitized.isNotBlank()) return sanitized
        }

        val withoutPhone = text.replace(Regex("(\\+?\\d[\\d\\s()\\-]{7,}\\d)"), " ")
        val stopWords = setOf(
            "reminder", "follow", "up", "set", "lagao", "laga", "do", "kar", "customer",
            "number", "mobile", "phone", "with", "country", "code", "please", "send",
            "date", "time", "today", "tomorrow", "kal", "aaj", "baje", "am", "pm",
            "naam", "name", "uploading", "downloading", "preparing", "media", "percent",
            "mb", "gb", "kb", "of"
        )
        val candidateTokens = withoutPhone
            .replace(Regex("[^\\p{L}\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { token ->
                token.length >= 2 &&
                    !stopWords.contains(token.lowercase(Locale.getDefault()))
            }
        if (candidateTokens.isEmpty()) return ""

        val candidateName = candidateTokens.take(3).joinToString(" ").trim()
        return sanitizeNameCandidate(candidateName)
    }

    private fun sanitizeNameCandidate(value: String): String {
        val cleaned = value
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return ""
        return sanitizeName(cleaned)
    }

    private fun isTransientProgressLikeText(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return false

        val normalized = text.lowercase(Locale.getDefault())
        val hasDataProgressPattern = Regex(
            "\\b\\d+(?:\\.\\d+)?\\s*(kb|mb|gb)\\s+of\\s+\\d+(?:\\.\\d+)?\\s*(kb|mb|gb)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        val hasPercentPattern = Regex("\\(\\d{1,3}%\\)").containsMatchIn(text)

        return normalized.startsWith("uploading:") ||
            normalized.startsWith("downloading:") ||
            normalized.startsWith("sending media") ||
            normalized.startsWith("preparing media") ||
            normalized.startsWith("uploading media") ||
            (hasDataProgressPattern && hasPercentPattern)
    }

    private fun getReminderTriggerKeywordList(): List<String> {
        return reminderTriggerKeywords
            .split(",", "\n")
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun hasReminderSchedulingIntent(instruction: String): Boolean {
        val lower = instruction.lowercase(Locale.getDefault())

        if (requireReminderTriggerKeyword) {
            val configuredKeywords = getReminderTriggerKeywordList()
            if (configuredKeywords.isEmpty()) return false
            return configuredKeywords.any { keyword -> lower.contains(keyword) }
        }

        val reminderKeywords =
            listOf(
                "reminder",
                "follow up",
                "follow-up",
                "schedule",
                "scheduled",
                "shedule",
                "shecdule",
                "shecdul",
                "set reminder",
                "set follow up",
                "set message time",
                "message time set",
                "time set",
                "set time",
                "yaad dila",
                "laga do",
                "lagao"
            )
        val hasKeyword = reminderKeywords.any { lower.contains(it) }

        val hasTemporalSignal =
            extractDateFromText(instruction) != null ||
                extractTimeFromText(instruction) != null ||
                Regex(
                    "(?i)\\b(kal|aaj|tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|am|pm|baje|at\\s*\\d{1,2}|\\d{1,2}:\\d{2})\\b"
                ).containsMatchIn(instruction)

        val hasSetAction = Regex(
            "(?i)\\b(set|schedule|scheduled|remind|follow\\s*up|laga|lagao|laga\\s*do|bhejna|send)\\b"
        ).containsMatchIn(instruction)

        return hasKeyword || (hasTemporalSignal && hasSetAction)
    }

    private fun buildReminderIntentHelpMessage(): String {
        val keywordHelp =
            if (requireReminderTriggerKeyword) {
                val keywords = getReminderTriggerKeywordList()
                if (keywords.isNotEmpty()) {
                    "Allowed trigger keywords: ${keywords.joinToString(", ")}"
                } else {
                    "Allowed trigger keywords: (none configured)"
                }
            } else {
                "Keyword guard: OFF (legacy intent detection active)"
            }

        return """
            Reminder auto schedule skipped.

            Reminder will only be set when your instruction clearly includes:
            - reminder / follow-up / schedule / message time set
            - customer name + mobile number (+country code)
            - exact outgoing message text
            - $keywordHelp

            Example:
            "Set a reminder for Asif tomorrow at 5:30 PM, number +919137167857, message: Please check the payment link."
        """.trimIndent()
    }

    private fun extractReminderMessageFromText(text: String): String {
        if (text.isBlank()) return ""

        val quoted = Regex("[\"“”]([^\"“”]{4,260})[\"“”]").find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        if (quoted.isNotBlank()) return quoted

        val explicitMessage = Regex(
            "(?i)\\b(?:message|msg|text)\\s*[:\\-]\\s*(.+)$"
        ).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (explicitMessage.isNotBlank()) return explicitMessage

        val hindiMessage = Regex(
            "(?i)\\b(?:bolna|bolo|kehna|kahna)\\s*[:\\-]\\s*(.+)$"
        ).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (hindiMessage.isNotBlank()) return hindiMessage

        return ""
    }

    private fun buildReminderDraftMessage(
        name: String,
        phone: String,
        date: String,
        time: String,
        context: String,
        outgoingMessage: String,
        needsName: Boolean,
        needsPhone: Boolean,
        needsMessage: Boolean
    ): String {
        val requirements = mutableListOf<String>()
        if (needsName) requirements += "Customer name"
        if (needsPhone) requirements += "Mobile number with country code (+...)"
        if (needsMessage) requirements += "Exact message text to send at the scheduled time"

        val requirementLines = requirements.mapIndexed { index, requirement ->
            "${index + 1}) $requirement"
        }.joinToString("\n")

        val examples = mutableListOf<String>()
        if (needsName) examples += "Name example: Asif"
        if (needsPhone) examples += "Number example: +919137167857"
        if (needsMessage) examples += "Message example: Message: Hi Asif, please check the payment link at 6 PM today."

        return """
            Reminder draft ready.

            Name    : ${name.ifBlank { "Not captured" }}
            Mobile  : ${phone.ifBlank { "Not captured" }}
            Date    : $date
            Time    : $time
            Topic   : $context
            Message : ${outgoingMessage.ifBlank { "Not captured" }}

            Action needed:
            $requirementLines

            ${examples.joinToString("\n")}
        """.trimIndent()
    }

    private fun buildNameAndPhoneRequestMessage(
        date: String,
        time: String,
        context: String,
        existingPhone: String = ""
    ): String {
        val phoneLine = if (existingPhone.isNotBlank()) {
            "Phone  : $existingPhone (please verify)"
        } else {
            "Phone  : Not captured"
        }

        return """
            Reminder draft ready.

            Name   : Not captured
            $phoneLine
            Date   : $date
            Time   : $time
            Topic  : $context

            Action needed:
            Please share customer name + mobile number with country code.
            Example: Name Asif, Number +919137167857
        """.trimIndent()
    }

    private fun buildNameRequestMessage(
        date: String,
        time: String,
        context: String,
        phone: String
    ): String {
        return """
            Reminder draft almost ready.

            Name   : Not captured
            Mobile : $phone
            Date   : $date
            Time   : $time
            Topic  : $context

            Action needed:
            Please share customer name.
            Example: Name Asif
        """.trimIndent()
    }

    private fun buildPhoneRequestMessage(
        name: String,
        date: String,
        time: String,
        context: String
    ): String {
        return """
            Reminder draft ready.

            Name   : $name
            Date   : $date
            Time   : $time
            Topic  : $context

            Action needed:
            Please send customer mobile number with country code.
            Example: +919137167857
        """.trimIndent()
    }

    private fun buildReminderConfirmationMessage(
        name: String,
        phone: String,
        date: String,
        time: String,
        context: String,
        outgoingMessage: String
    ): String {
        return """
            Reminder Scheduled Successfully

            Name   : $name
            Mobile : $phone
            Date   : $date
            Time   : $time
            Topic  : $context
            Message: $outgoingMessage

            Status : Follow-up queued. The message will be sent automatically at the scheduled time.
        """.trimIndent()
    }

    private fun getPendingReminder(): PendingReminder? {
        val contextValue = prefs.getString(KEY_PENDING_CONTEXT, "")?.trim().orEmpty()
        val originalInstruction = prefs.getString(KEY_PENDING_ORIGINAL_INSTRUCTION, "")?.trim().orEmpty()
        if (contextValue.isBlank() && originalInstruction.isBlank()) return null

        val createdAt = prefs.getLong(KEY_PENDING_CREATED_AT, 0L)
        if (createdAt > 0L && (System.currentTimeMillis() - createdAt) > PENDING_REMINDER_TTL_MS) {
            clearPendingReminder()
            return null
        }

        return PendingReminder(
            name = prefs.getString(KEY_PENDING_NAME, "")?.trim().orEmpty(),
            phone = prefs.getString(KEY_PENDING_PHONE, "")?.trim().orEmpty(),
            context = contextValue,
            outgoingMessage = prefs.getString(KEY_PENDING_MESSAGE, "")?.trim().orEmpty(),
            date = prefs.getString(KEY_PENDING_DATE, "")?.trim().orEmpty(),
            time = prefs.getString(KEY_PENDING_TIME, "")?.trim().orEmpty(),
            originalInstruction = originalInstruction,
            createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
            requiresName = prefs.getBoolean(
                KEY_PENDING_REQUIRES_NAME,
                prefs.getString(KEY_PENDING_NAME, "")?.trim().isNullOrBlank()
            ),
            requiresPhone = prefs.getBoolean(KEY_PENDING_REQUIRES_PHONE, true),
            requiresMessage = prefs.getBoolean(
                KEY_PENDING_REQUIRES_MESSAGE,
                prefs.getString(KEY_PENDING_MESSAGE, "")?.trim().isNullOrBlank()
            )
        )
    }

    private fun savePendingReminder(pending: PendingReminder) {
        prefs.edit()
            .putString(KEY_PENDING_NAME, pending.name)
            .putString(KEY_PENDING_PHONE, pending.phone)
            .putString(KEY_PENDING_CONTEXT, pending.context)
            .putString(KEY_PENDING_MESSAGE, pending.outgoingMessage)
            .putString(KEY_PENDING_DATE, pending.date)
            .putString(KEY_PENDING_TIME, pending.time)
            .putString(KEY_PENDING_ORIGINAL_INSTRUCTION, pending.originalInstruction)
            .putLong(KEY_PENDING_CREATED_AT, pending.createdAt)
            .putBoolean(KEY_PENDING_REQUIRES_NAME, pending.requiresName)
            .putBoolean(KEY_PENDING_REQUIRES_PHONE, pending.requiresPhone)
            .putBoolean(KEY_PENDING_REQUIRES_MESSAGE, pending.requiresMessage)
            .apply()
    }

    private fun clearPendingReminder() {
        prefs.edit()
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_PHONE)
            .remove(KEY_PENDING_CONTEXT)
            .remove(KEY_PENDING_MESSAGE)
            .remove(KEY_PENDING_DATE)
            .remove(KEY_PENDING_TIME)
            .remove(KEY_PENDING_ORIGINAL_INSTRUCTION)
            .remove(KEY_PENDING_CREATED_AT)
            .remove(KEY_PENDING_REQUIRES_NAME)
            .remove(KEY_PENDING_REQUIRES_PHONE)
            .remove(KEY_PENDING_REQUIRES_MESSAGE)
            .apply()
    }

    private fun normalizeOwnerPhone(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return ""

        val compact = raw.replace(Regex("[\\s\\-()]"), "")
        val withPlus = when {
            compact.startsWith("+") -> compact
            compact.startsWith("00") && compact.length > 2 -> "+${compact.drop(2)}"
            compact.firstOrNull()?.isDigit() == true -> "+$compact"
            else -> compact
        }

        val digits = withPlus.filter { it.isDigit() }
        if (digits.length < 7) return if (withPlus.startsWith("+")) "+$digits" else ""

        return "+$digits"
    }

    private data class KnownContact(
        val name: String,
        val phone: String
    )

    private data class PendingReminder(
        val name: String,
        val phone: String,
        val context: String,
        val outgoingMessage: String,
        val date: String,
        val time: String,
        val originalInstruction: String,
        val createdAt: Long,
        val requiresName: Boolean,
        val requiresPhone: Boolean,
        val requiresMessage: Boolean
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        const val KEY_REQUIRE_REMINDER_TRIGGER_KEYWORD = "require_reminder_trigger_keyword"
        const val KEY_REMINDER_TRIGGER_KEYWORDS = "reminder_trigger_keywords"
        const val DEFAULT_REMINDER_TRIGGER_KEYWORDS =
            "reminder,schedule,scheduled,shecduled,follow up,follow-up,set reminder,message time set,yaad dila"
        const val KEY_PENDING_NAME = "pending_reminder_name"
        const val KEY_PENDING_PHONE = "pending_reminder_phone"
        const val KEY_PENDING_CONTEXT = "pending_reminder_context"
        const val KEY_PENDING_MESSAGE = "pending_reminder_message"
        const val KEY_PENDING_DATE = "pending_reminder_date"
        const val KEY_PENDING_TIME = "pending_reminder_time"
        const val KEY_PENDING_ORIGINAL_INSTRUCTION = "pending_reminder_original_instruction"
        const val KEY_PENDING_CREATED_AT = "pending_reminder_created_at"
        const val KEY_PENDING_REQUIRES_NAME = "pending_reminder_requires_name"
        const val KEY_PENDING_REQUIRES_PHONE = "pending_reminder_requires_phone"
        const val KEY_PENDING_REQUIRES_MESSAGE = "pending_reminder_requires_message"
        const val KEY_PENDING_EXPORT_REQUEST = "pending_export_request"
        const val KEY_PENDING_EXPORT_CREATED_AT = "pending_export_created_at"
        const val PENDING_REMINDER_TTL_MS = 24L * 60L * 60L * 1000L
    }

    private data class AIParsedInstruction(
        @SerializedName("name") val name: String?,
        @SerializedName("phone") val phone: String?,
        @SerializedName("context") val context: String?,
        @SerializedName("date") val date: String?,
        @SerializedName("time") val time: String?,
        @SerializedName("outgoing_message") val outgoingMessage: String?
    )
}

