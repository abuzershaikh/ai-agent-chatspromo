package com.message.bulksend

import androidx.compose.ui.graphics.Color


// Color theme data class
data class ColorTheme(
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: List<Color>,
    val cardColor: Color
)

// Define the color themes
val colorThemes = listOf(
    ColorTheme(
        name = "Default",
        primaryColor = Color(0xFF0288D1),
        secondaryColor = Color(0xFF03A9F4),
        backgroundColor = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0), Color(0xFFF1F5F9)),
        cardColor = Color.White
    ),
    ColorTheme(
        name = "Ocean",
        primaryColor = Color(0xFF00796B),
        secondaryColor = Color(0xFF4DB6AC),
        backgroundColor = listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB)),
        cardColor = Color(0xFFF0F5F5)
    ),
    ColorTheme(
        name = "Sunset",
        primaryColor = Color(0xFFE64A19),
        secondaryColor = Color(0xFFFF8A65),
        backgroundColor = listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)),
        cardColor = Color(0xFFFFF8F2)
    ),
    ColorTheme(
        name = "Forest",
        primaryColor = Color(0xFF388E3C),
        secondaryColor = Color(0xFF81C784),
        backgroundColor = listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9)),
        cardColor = Color(0xFFF1F8F1)
    ),
    ColorTheme(
        name = "Plum",
        primaryColor = Color(0xFF7B1FA2),
        secondaryColor = Color(0xFFBA68C8),
        backgroundColor = listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7)),
        cardColor = Color(0xFFFAF5FB)
    )
)
