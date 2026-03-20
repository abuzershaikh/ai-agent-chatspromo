package com.message.bulksend.product

import org.json.JSONArray
import org.json.JSONObject

enum class CustomFieldType(val displayName: String, val icon: String) {
    TEXT("Text", "📝"),
    NUMBER("Number", "#️⃣"),
    DATE("Date", "📅"),
    TIME("Time", "⏰"),
    LINK("Link", "🔗"),
    IMAGE("Image", "🖼️"),
    MULTI_TEXT("Multi Text", "📄")
}

data class CustomField(
    val name: String,
    val type: CustomFieldType,
    val value: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("type", type.name)
        put("value", value)
    }

    companion object {
        fun fromJson(json: JSONObject): CustomField = CustomField(
            name = json.optString("name", ""),
            type = try { CustomFieldType.valueOf(json.optString("type", "TEXT")) } catch (e: Exception) { CustomFieldType.TEXT },
            value = json.optString("value", "")
        )

        fun listToJson(fields: List<CustomField>): String {
            val arr = JSONArray()
            fields.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<CustomField> {
            if (json.isBlank() || json == "[]" || json == "{}") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) { emptyList() }
        }
    }
}

data class MediaItem(
    val path: String,
    val isVideo: Boolean = false,
    val isPdf: Boolean = false,
    val isAudio: Boolean = false
) {
    // Helper to determine media type
    val mediaType: MediaType
        get() = when {
            isPdf -> MediaType.PDF
            isAudio -> MediaType.AUDIO
            isVideo -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
    
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path)
        put("isVideo", isVideo)
        put("isPdf", isPdf)
        put("isAudio", isAudio)
    }

    companion object {
        fun fromJson(json: JSONObject): MediaItem = MediaItem(
            path = json.optString("path", ""),
            isVideo = json.optBoolean("isVideo", false),
            isPdf = json.optBoolean("isPdf", false),
            isAudio = json.optBoolean("isAudio", false)
        )

        fun listToJson(items: List<MediaItem>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<MediaItem> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) { emptyList() }
        }
    }
}

enum class MediaType(val displayName: String, val icon: String) {
    IMAGE("Image", "🖼️"),
    VIDEO("Video", "🎥"),
    PDF("PDF", "📄"),
    AUDIO("Audio", "🎵")
}
