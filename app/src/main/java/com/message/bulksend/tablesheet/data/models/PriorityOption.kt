package com.message.bulksend.tablesheet.data.models

import androidx.compose.ui.graphics.Color

/**
 * Priority Option with name and color
 */
data class PriorityOption(
    val name: String,
    val color: Color
) {
    companion object {
        // Default priority options
        val DEFAULT_OPTIONS = listOf(
            PriorityOption("High", Color(0xFFEF4444)),      // Red
            PriorityOption("Medium", Color(0xFFF59E0B)),    // Orange
            PriorityOption("Low", Color(0xFF10B981))        // Green
        )
        
        // Predefined colors for priority options
        val AVAILABLE_COLORS = listOf(
            Color(0xFFEF4444), // Red
            Color(0xFFF59E0B), // Orange
            Color(0xFF10B981), // Green
            Color(0xFF3B82F6), // Blue
            Color(0xFF8B5CF6), // Purple
            Color(0xFFEC4899), // Pink
            Color(0xFF06B6D4), // Cyan
            Color(0xFF84CC16), // Lime
            Color(0xFFF97316), // Orange-600
            Color(0xFF6366F1), // Indigo
            Color(0xFF14B8A6), // Teal
            Color(0xFFEAB308)  // Yellow
        )
        
        /**
         * Parse priority options from string format: "High:#EF4444,Medium:#F59E0B,Low:#10B981"
         */
        fun parseFromString(optionsString: String?): List<PriorityOption> {
            if (optionsString.isNullOrBlank()) return DEFAULT_OPTIONS
            
            return try {
                optionsString.split(",").mapNotNull { option ->
                    val parts = option.split(":")
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val colorHex = parts[1].trim()
                        val color = parseColor(colorHex)
                        if (color != null) PriorityOption(name, color) else null
                    } else null
                }
            } catch (e: Exception) {
                DEFAULT_OPTIONS
            }
        }
        
        /**
         * Convert priority options to string format: "High:#EF4444,Medium:#F59E0B,Low:#10B981"
         */
        fun toString(options: List<PriorityOption>): String {
            return options.joinToString(",") { option ->
                "${option.name}:${colorToHex(option.color)}"
            }
        }
        
        /**
         * Parse color from hex string
         */
        private fun parseColor(hex: String): Color? {
            return try {
                val cleanHex = hex.removePrefix("#")
                val colorInt = cleanHex.toLong(16)
                Color(0xFF000000 or colorInt)
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Convert color to hex string
         */
        private fun colorToHex(color: Color): String {
            val red = (color.red * 255).toInt()
            val green = (color.green * 255).toInt()
            val blue = (color.blue * 255).toInt()
            return "#%02X%02X%02X".format(red, green, blue)
        }
    }
}