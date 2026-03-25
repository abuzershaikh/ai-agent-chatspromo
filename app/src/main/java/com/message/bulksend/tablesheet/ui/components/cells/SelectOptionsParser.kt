package com.message.bulksend.tablesheet.ui.components.cells

import org.json.JSONArray

internal fun parseSelectOptions(raw: String?): List<String> {
    val input = raw?.trim().orEmpty()
    if (input.isEmpty()) return emptyList()

    // Backward compatibility for JSON-array storage: ["YES","NO"]
    if (input.startsWith("[") && input.endsWith("]")) {
        val fromJson = runCatching {
            buildList {
                val array = JSONArray(input)
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim().trim('"')
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())

        if (fromJson.isNotEmpty()) return fromJson
    }

    // CSV / semicolon / newline compatibility.
    return input
        .replace("\r", "\n")
        .split(',', ';', '\n')
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
}
