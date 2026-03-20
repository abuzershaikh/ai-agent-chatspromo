package com.message.bulksend.notes

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Enhanced Note Data Class with categories and colors
data class Note(
    val id: Long,
    var title: String,
    var content: String,
    val lastModified: Long,
    val uri: Uri,
    val category: NoteCategory = NoteCategory.GENERAL,
    val colorTheme: NoteColorTheme = NoteColorTheme.DEFAULT,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val tags: List<String> = emptyList()
)

// Note Categories
enum class NoteCategory(val displayName: String, val icon: ImageVector) {
    GENERAL("General", Icons.Default.Description),
    WORK("Work", Icons.Default.Work),
    PERSONAL("Personal", Icons.Default.Person),
    IDEAS("Ideas", Icons.Default.Lightbulb),
    SHOPPING("Shopping", Icons.Default.ShoppingCart),
    TRAVEL("Travel", Icons.Default.Flight),
    HEALTH("Health", Icons.Default.FitnessCenter),
    FINANCE("Finance", Icons.Default.AttachMoney)
}

// Color Themes for Notes - Beautiful Dark Theme with Multiple Colors
enum class NoteColorTheme(val primaryColor: Color, val textColor: Color, val displayName: String) {
    // Dark themes with blue accents
    MIDNIGHT_BLUE(Color(0xFF1A1A2E), Color(0xFFE0E6ED), "Midnight Blue"),
    DEEP_OCEAN(Color(0xFF16213E), Color(0xFFE0E6ED), "Deep Ocean"),
    ROYAL_BLUE(Color(0xFF0F3460), Color(0xFFE0E6ED), "Royal Blue"),
    STEEL_BLUE(Color(0xFF2C3E50), Color(0xFFE0E6ED), "Steel Blue"),
    
    // Blue gradient themes
    ELECTRIC_BLUE(Color(0xFF1E3A8A), Color(0xFFE0E6ED), "Electric Blue"),
    CYBER_BLUE(Color(0xFF1E40AF), Color(0xFFE0E6ED), "Cyber Blue"),
    NAVY_BLUE(Color(0xFF1E293B), Color(0xFFE0E6ED), "Navy Blue"),
    
    // Pink themes
    ROSE_PINK(Color(0xFF831843), Color(0xFFE0E6ED), "Rose Pink"),
    MAGENTA_DARK(Color(0xFF701A75), Color(0xFFE0E6ED), "Magenta Dark"),
    PINK_PURPLE(Color(0xFF86198F), Color(0xFFE0E6ED), "Pink Purple"),
    
    // Yellow/Orange themes
    AMBER_DARK(Color(0xFF92400E), Color(0xFFE0E6ED), "Amber Dark"),
    GOLDEN_YELLOW(Color(0xFF78350F), Color(0xFFE0E6ED), "Golden Yellow"),
    ORANGE_DARK(Color(0xFF9A3412), Color(0xFFE0E6ED), "Orange Dark"),
    
    // Green themes
    FOREST_GREEN(Color(0xFF14532D), Color(0xFFE0E6ED), "Forest Green"),
    EMERALD_DARK(Color(0xFF064E3B), Color(0xFFE0E6ED), "Emerald Dark"),
    SAGE_GREEN(Color(0xFF365314), Color(0xFFE0E6ED), "Sage Green"),
    
    // Purple themes
    DEEP_PURPLE(Color(0xFF581C87), Color(0xFFE0E6ED), "Deep Purple"),
    VIOLET_DARK(Color(0xFF4C1D95), Color(0xFFE0E6ED), "Violet Dark"),
    
    // Black themes
    CARBON_BLACK(Color(0xFF0F172A), Color(0xFFE0E6ED), "Carbon Black"),
    SPACE_BLACK(Color(0xFF111827), Color(0xFFE0E6ED), "Space Black"),
    
    // Default for compatibility
    DEFAULT(Color(0xFF1A1A2E), Color(0xFFE0E6ED), "Default")
}

// Sort Options
enum class SortOrder(val displayName: String) {
    LATEST_FIRST("Latest First"),
    OLDEST_FIRST("Oldest First"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    CATEGORY("By Category"),
    FAVORITES_FIRST("Favorites First")
}

// File Helper for saving/loading notes
object FileHelper {
    private const val NOTES_DIR = "smart_notes"
    
    fun getNotesDirectory(context: Context): File {
        val dir = File(context.filesDir, NOTES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    fun saveNoteToFile(context: Context, note: Note) {
        val notesDir = getNotesDirectory(context)
        val fileName = "${note.id}.txt"
        val file = File(notesDir, fileName)
        
        val content = buildString {
            appendLine("TITLE:${note.title}")
            appendLine("CATEGORY:${note.category.name}")
            appendLine("COLOR:${note.colorTheme.name}")
            appendLine("FAVORITE:${note.isFavorite}")
            appendLine("PINNED:${note.isPinned}")
            appendLine("DELETED:${note.isDeleted}")
            appendLine("TAGS:${note.tags.joinToString(",")}")
            appendLine("MODIFIED:${note.lastModified}")
            appendLine("CONTENT:")
            append(note.content)
        }
        
        file.writeText(content)
    }
    
    fun fetchNotesFromDirectory(context: Context): List<Note> {
        val notesDir = getNotesDirectory(context)
        val pinnedIds = PreferencesHelper.loadPinnedNoteIds(context)
        
        return notesDir.listFiles()?.mapNotNull { file ->
            try {
                val lines = file.readLines()
                val title = lines.find { it.startsWith("TITLE:") }?.substringAfter("TITLE:") ?: "Untitled"
                val categoryName = lines.find { it.startsWith("CATEGORY:") }?.substringAfter("CATEGORY:") ?: "GENERAL"
                val colorName = lines.find { it.startsWith("COLOR:") }?.substringAfter("COLOR:") ?: "DEFAULT"
                val isFavorite = lines.find { it.startsWith("FAVORITE:") }?.substringAfter("FAVORITE:")?.toBoolean() ?: false
                val isPinned = lines.find { it.startsWith("PINNED:") }?.substringAfter("PINNED:")?.toBoolean() ?: false
                val isDeleted = lines.find { it.startsWith("DELETED:") }?.substringAfter("DELETED:")?.toBoolean() ?: false
                val tagsString = lines.find { it.startsWith("TAGS:") }?.substringAfter("TAGS:") ?: ""
                val tags = if (tagsString.isBlank()) emptyList() else tagsString.split(",")
                val modified = lines.find { it.startsWith("MODIFIED:") }?.substringAfter("MODIFIED:")?.toLongOrNull() ?: System.currentTimeMillis()
                
                val contentStartIndex = lines.indexOfFirst { it == "CONTENT:" }
                val content = if (contentStartIndex >= 0 && contentStartIndex < lines.size - 1) {
                    lines.subList(contentStartIndex + 1, lines.size).joinToString("\n")
                } else ""
                
                val noteId = file.nameWithoutExtension.toLongOrNull() ?: System.currentTimeMillis()
                
                Note(
                    id = noteId,
                    title = title,
                    content = content,
                    lastModified = modified,
                    uri = Uri.fromFile(file),
                    category = try { NoteCategory.valueOf(categoryName) } catch (e: Exception) { NoteCategory.GENERAL },
                    colorTheme = try { NoteColorTheme.valueOf(colorName) } catch (e: Exception) { NoteColorTheme.DEFAULT },
                    isFavorite = isFavorite,
                    isPinned = pinnedIds.contains(noteId.toString()),
                    isDeleted = isDeleted,
                    tags = tags
                )
            } catch (e: Exception) {
                null
            }
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    fun deleteNotePermanently(context: Context, uri: Uri) {
        try {
            val file = File(uri.path ?: return)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Preferences Helper
object PreferencesHelper {
    private const val PREFS_NAME = "NotesPrefs"
    private const val KEY_PINNED_IDS = "pinned_note_ids"
    
    fun savePinnedNoteIds(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PINNED_IDS, ids).apply()
    }
    
    fun loadPinnedNoteIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PINNED_IDS, emptySet()) ?: emptySet()
    }
}
