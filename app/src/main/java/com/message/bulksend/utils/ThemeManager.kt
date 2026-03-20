package com.message.bulksend.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

/**
 * Theme Manager class to handle theme preferences across activities
 */
object ThemeManager {
    private const val PREFS_NAME = "ThemePreferences"
    private const val KEY_SELECTED_THEME = "selectedTheme"
    private const val KEY_USE_ENHANCED_UI = "useEnhancedUI"

    // Color theme data class
    data class ColorTheme(
        val name: String,
        val primaryColor: Color,
        val secondaryColor: Color,
        val backgroundColor: List<Color>,
        val cardColor: Color
    )

    // Predefined color themes
    val colorThemes = listOf(
        ColorTheme(
            name = "Default",
            primaryColor = Color(0xFF475569),
            secondaryColor = Color(0xFF64748B),
            backgroundColor = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0), Color(0xFFF1F5F9)),
            cardColor = Color.White
        ),
        ColorTheme(
            name = "Ocean Blue",
            primaryColor = Color(0xFF667eea),
            secondaryColor = Color(0xFF764ba2),
            backgroundColor = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0), Color(0xFFF1F5F9)),
            cardColor = Color.White
        ),
        ColorTheme(
            name = "Sunset Orange",
            primaryColor = Color(0xFFFF6B6B),
            secondaryColor = Color(0xFFFFE66D),
            backgroundColor = listOf(Color(0xFFFFF8F0), Color(0xFFFFE4CC), Color(0xFFFFF0E6)),
            cardColor = Color(0xFFFFFAF5)
        ),
        ColorTheme(
            name = "Forest Green",
            primaryColor = Color(0xFF4ECDC4),
            secondaryColor = Color(0xFF44A08D),
            backgroundColor = listOf(Color(0xFFF0FFF4), Color(0xFFDCFCE7), Color(0xFFECFDF5)),
            cardColor = Color(0xFFF7FEFC)
        ),
        ColorTheme(
            name = "Purple Dream",
            primaryColor = Color(0xFF9C27B0),
            secondaryColor = Color(0xFFE91E63),
            backgroundColor = listOf(Color(0xFFFCF4FF), Color(0xFFF3E5F5), Color(0xFFF8F0FF)),
            cardColor = Color(0xFFFDF7FF)
        ),
        ColorTheme(
            name = "Golden Hour",
            primaryColor = Color(0xFFFFB74D),
            secondaryColor = Color(0xFFFF8A65),
            backgroundColor = listOf(Color(0xFFFFFBF0), Color(0xFFFFF3E0), Color(0xFFFFF8E1)),
            cardColor = Color(0xFFFFFDF7)
        )
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get selected theme index
     */
    fun getSelectedThemeIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_THEME, 0)
    }

    /**
     * Save selected theme index
     */
    fun saveSelectedThemeIndex(context: Context, themeIndex: Int) {
        getPrefs(context).edit().putInt(KEY_SELECTED_THEME, themeIndex).apply()
    }

    /**
     * Get current theme
     */
    fun getCurrentTheme(context: Context): ColorTheme {
        val index = getSelectedThemeIndex(context)
        return colorThemes.getOrNull(index) ?: colorThemes[0]
    }

    /**
     * Check if user prefers enhanced UI
     */
    fun useEnhancedUI(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_ENHANCED_UI, false)
    }

    /**
     * Save enhanced UI preference
     */
    fun saveEnhancedUIPreference(context: Context, useEnhanced: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_ENHANCED_UI, useEnhanced).apply()
    }

    /**
     * Reset to default settings
     */
    fun resetToDefault(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}