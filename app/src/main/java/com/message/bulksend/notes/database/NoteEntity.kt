package com.message.bulksend.notes.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.message.bulksend.notes.NoteCategory
import com.message.bulksend.notes.NoteColorTheme

@Entity(tableName = "notes")
@TypeConverters(Converters::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: NoteCategory = NoteCategory.GENERAL,
    val colorTheme: NoteColorTheme = NoteColorTheme.DEFAULT,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromNoteCategory(category: NoteCategory): String {
        return category.name
    }

    @TypeConverter
    fun toNoteCategory(categoryName: String): NoteCategory {
        return try {
            NoteCategory.valueOf(categoryName)
        } catch (e: Exception) {
            NoteCategory.GENERAL
        }
    }

    @TypeConverter
    fun fromNoteColorTheme(colorTheme: NoteColorTheme): String {
        return colorTheme.name
    }

    @TypeConverter
    fun toNoteColorTheme(colorThemeName: String): NoteColorTheme {
        return try {
            NoteColorTheme.valueOf(colorThemeName)
        } catch (e: Exception) {
            NoteColorTheme.DEFAULT
        }
    }

    @TypeConverter
    fun fromStringList(tags: List<String>): String {
        return tags.joinToString(",")
    }

    @TypeConverter
    fun toStringList(tagsString: String): List<String> {
        return if (tagsString.isBlank()) emptyList() else tagsString.split(",")
    }
}