package com.message.bulksend.autorespond.ai.needdiscovery

import android.content.Context
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import java.util.Locale

class NeedDiscoveryManager(context: Context) {

    private val appContext = context.applicationContext
    private val settings = AIAgentSettingsManager(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSchema(): NeedDiscoverySchema {
        return NeedDiscoveryCodec.schemaFromJson(settings.customTemplateNeedDiscoverySchemaJson)
    }

    fun saveSchema(schema: NeedDiscoverySchema) {
        settings.customTemplateNeedDiscoverySchemaJson = NeedDiscoveryCodec.schemaToJson(schema)
    }

    fun getState(senderPhone: String): NeedDiscoveryState? {
        if (senderPhone.isBlank()) return null
        return loadStates().firstOrNull { it.senderPhone == senderPhone }
    }

    fun updateFromIncomingMessage(senderPhone: String, userMessage: String): NeedDiscoveryState {
        return probe(senderPhone, userMessage)
            .let { result ->
                NeedDiscoveryState(
                    senderPhone = senderPhone,
                    knownValues = result.knownValues,
                    missingRequiredFieldIds = result.missingRequiredFieldIds,
                    suggestedQuestion = result.suggestedQuestion,
                    updatedAt = System.currentTimeMillis()
                )
            }
    }

    fun probe(senderPhone: String, userMessage: String): NeedDiscoveryProbeResult {
        val schema = getSchema()
        if (schema.allFields().isEmpty()) {
            return NeedDiscoveryProbeResult(
                knownValues = emptyMap(),
                missingRequiredFieldIds = emptyList(),
                suggestedQuestion = "",
                closureReady = true
            )
        }

        val existingState = getState(senderPhone)
        val known = existingState?.knownValues?.toMutableMap() ?: mutableMapOf()
        applyHeuristicExtraction(schema, userMessage, known)

        val missingRequired =
            schema.requiredFields
                .map { it.id }
                .filter { id -> known[id].isNullOrBlank() }

        val nextQuestion =
            schema.requiredFields
                .firstOrNull { it.id in missingRequired }
                ?.question
                .orEmpty()

        val updatedState =
            NeedDiscoveryState(
                senderPhone = senderPhone,
                knownValues = known,
                missingRequiredFieldIds = missingRequired,
                suggestedQuestion = nextQuestion,
                updatedAt = System.currentTimeMillis()
            )
        upsertState(updatedState)

        return NeedDiscoveryProbeResult(
            knownValues = known,
            missingRequiredFieldIds = missingRequired,
            suggestedQuestion = nextQuestion,
            closureReady = missingRequired.isEmpty()
        )
    }

    fun buildContextSnippet(senderPhone: String): String {
        val schema = getSchema()
        if (schema.allFields().isEmpty()) return ""

        val state = getState(senderPhone)
        val known = state?.knownValues.orEmpty()
        val missing =
            schema.requiredFields
                .filter { known[it.id].isNullOrBlank() }
                .map { it.id }

        val sb = StringBuilder()
        sb.append("[NEED DISCOVERY]\n")
        sb.append("Configured Required Fields: ")
        sb.append(schema.requiredFields.joinToString { it.label })
        sb.append("\n")

        if (known.isNotEmpty()) {
            sb.append("Known Values:\n")
            schema.allFields().forEach { field ->
                val value = known[field.id].orEmpty()
                if (value.isNotBlank()) {
                    sb.append("- ${field.label}: $value\n")
                }
            }
        }

        if (missing.isNotEmpty()) {
            sb.append("Missing Required Fields: ${missing.joinToString()}\n")
            val nextQuestion =
                schema.requiredFields.firstOrNull { it.id in missing }?.question.orEmpty()
            if (nextQuestion.isNotBlank()) {
                sb.append("Next Best Question: $nextQuestion\n")
            }
        } else {
            sb.append("All required fields captured.\n")
        }

        if (schema.closureConditions.isNotBlank()) {
            sb.append("Closure Conditions: ${schema.closureConditions}\n")
        }

        return sb.toString().trimEnd()
    }

    private fun applyHeuristicExtraction(
        schema: NeedDiscoverySchema,
        message: String,
        known: MutableMap<String, String>
    ) {
        val lower = message.lowercase(Locale.ROOT)

        val kvRegex = Regex("([a-zA-Z0-9_ ]{2,40})\\s*[:=]\\s*([^,;\\n]{2,100})")
        kvRegex.findAll(message).forEach { match ->
            val key = match.groupValues[1].trim().lowercase(Locale.ROOT)
            val value = match.groupValues[2].trim()
            if (value.isBlank()) return@forEach
            val field = findFieldForKey(schema, key) ?: return@forEach
            known[field.id] = value
        }

        schema.allFields().forEach { field ->
            if (!known[field.id].isNullOrBlank()) return@forEach

            val aliases = buildAliases(field)
            aliases.forEach { alias ->
                if (alias.isBlank()) return@forEach
                val escaped = Regex.escape(alias)
                val regex = Regex(
                    "(?:my\\s+)?$escaped\\s*(?:is|=|:|-)?\\s*([a-zA-Z0-9@+.,/\\- ]{2,80})",
                    RegexOption.IGNORE_CASE
                )
                val match = regex.find(message) ?: return@forEach
                val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (value.isNotBlank()) {
                    known[field.id] = value
                    return@forEach
                }
            }

            if (field.id.equals("name", ignoreCase = true) && lower.contains("my name is ")) {
                val value = lower.substringAfter("my name is ").trim().take(60)
                if (value.isNotBlank()) {
                    known[field.id] = message.substringAfter("my name is ").trim().take(60)
                }
            }
        }
    }

    private fun findFieldForKey(schema: NeedDiscoverySchema, key: String): NeedDiscoveryField? {
        return schema.allFields().firstOrNull { field ->
            buildAliases(field).any { alias -> alias.equals(key, ignoreCase = true) }
        }
    }

    private fun buildAliases(field: NeedDiscoveryField): List<String> {
        return buildList {
            add(field.id.trim())
            add(field.label.trim())
            field.keywords.forEach { add(it.trim()) }
        }.filter { it.isNotBlank() }
    }

    private fun upsertState(state: NeedDiscoveryState) {
        val all = loadStates().toMutableList()
        val index = all.indexOfFirst { it.senderPhone == state.senderPhone }
        if (index >= 0) {
            all[index] = state
        } else {
            all += state
        }
        saveStates(all)
    }

    private fun loadStates(): List<NeedDiscoveryState> {
        val raw = prefs.getString(KEY_STATES, "[]") ?: "[]"
        return NeedDiscoveryCodec.statesFromJson(raw)
    }

    private fun saveStates(states: List<NeedDiscoveryState>) {
        prefs.edit().putString(KEY_STATES, NeedDiscoveryCodec.statesToJson(states)).apply()
    }

    companion object {
        private const val PREFS_NAME = "need_discovery_state"
        private const val KEY_STATES = "states_json"
    }
}
