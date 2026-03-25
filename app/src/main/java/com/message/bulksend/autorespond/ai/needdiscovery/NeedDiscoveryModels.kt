package com.message.bulksend.autorespond.ai.needdiscovery

import org.json.JSONArray
import org.json.JSONObject

data class NeedDiscoveryField(
    val id: String,
    val label: String,
    val question: String,
    val required: Boolean = true,
    val keywords: List<String> = emptyList()
)

data class NeedDiscoverySchema(
    val requiredFields: List<NeedDiscoveryField> = emptyList(),
    val optionalFields: List<NeedDiscoveryField> = emptyList(),
    val closureConditions: String = ""
) {
    fun allFields(): List<NeedDiscoveryField> = requiredFields + optionalFields
}

data class NeedDiscoveryState(
    val senderPhone: String,
    val knownValues: Map<String, String> = emptyMap(),
    val missingRequiredFieldIds: List<String> = emptyList(),
    val suggestedQuestion: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class NeedDiscoveryProbeResult(
    val knownValues: Map<String, String>,
    val missingRequiredFieldIds: List<String>,
    val suggestedQuestion: String,
    val closureReady: Boolean
)

object NeedDiscoveryCodec {

    fun schemaFromJson(raw: String): NeedDiscoverySchema {
        if (raw.isBlank()) return NeedDiscoverySchema()
        return try {
            val obj = JSONObject(raw)
            NeedDiscoverySchema(
                requiredFields = parseFields(obj.optJSONArray("requiredFields"), true),
                optionalFields = parseFields(obj.optJSONArray("optionalFields"), false),
                closureConditions = obj.optString("closureConditions").trim()
            )
        } catch (_: Exception) {
            NeedDiscoverySchema()
        }
    }

    fun schemaToJson(schema: NeedDiscoverySchema): String {
        return JSONObject()
            .put("requiredFields", fieldsToJson(schema.requiredFields))
            .put("optionalFields", fieldsToJson(schema.optionalFields))
            .put("closureConditions", schema.closureConditions.trim())
            .toString()
    }

    fun statesFromJson(raw: String): List<NeedDiscoveryState> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<NeedDiscoveryState>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val known = mutableMapOf<String, String>()
                val knownObj = obj.optJSONObject("knownValues") ?: JSONObject()
                val keys = knownObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = knownObj.optString(key).trim()
                    if (value.isNotBlank()) known[key] = value
                }
                out += NeedDiscoveryState(
                    senderPhone = obj.optString("senderPhone"),
                    knownValues = known,
                    missingRequiredFieldIds = parseStringArray(obj.optJSONArray("missingRequiredFieldIds")),
                    suggestedQuestion = obj.optString("suggestedQuestion"),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun statesToJson(states: List<NeedDiscoveryState>): String {
        val arr = JSONArray()
        states.forEach { state ->
            val knownObj = JSONObject()
            state.knownValues.forEach { (key, value) -> knownObj.put(key, value) }
            arr.put(
                JSONObject()
                    .put("senderPhone", state.senderPhone)
                    .put("knownValues", knownObj)
                    .put("missingRequiredFieldIds", JSONArray(state.missingRequiredFieldIds))
                    .put("suggestedQuestion", state.suggestedQuestion)
                    .put("updatedAt", state.updatedAt)
            )
        }
        return arr.toString()
    }

    private fun parseFields(array: JSONArray?, requiredDefault: Boolean): List<NeedDiscoveryField> {
        if (array == null) return emptyList()
        val out = mutableListOf<NeedDiscoveryField>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            val label = obj.optString("label").trim()
            val question = obj.optString("question").trim()
            if (id.isBlank() || label.isBlank() || question.isBlank()) continue
            out += NeedDiscoveryField(
                id = id,
                label = label,
                question = question,
                required = obj.optBoolean("required", requiredDefault),
                keywords = parseStringArray(obj.optJSONArray("keywords"))
            )
        }
        return out
    }

    private fun fieldsToJson(fields: List<NeedDiscoveryField>): JSONArray {
        val arr = JSONArray()
        fields.forEach { field ->
            arr.put(
                JSONObject()
                    .put("id", field.id)
                    .put("label", field.label)
                    .put("question", field.question)
                    .put("required", field.required)
                    .put("keywords", JSONArray(field.keywords))
            )
        }
        return arr
    }

    private fun parseStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotBlank()) out += value
        }
        return out
    }
}
