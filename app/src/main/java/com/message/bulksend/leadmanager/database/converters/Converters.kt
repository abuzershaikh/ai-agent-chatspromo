package com.message.bulksend.leadmanager.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.leadmanager.model.*
import com.message.bulksend.leadmanager.database.entities.TimelineEventType
import com.message.bulksend.leadmanager.notes.NoteType
import com.message.bulksend.leadmanager.notes.NotePriority

class Converters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromLeadStatus(status: LeadStatus): String = status.name
    
    @TypeConverter
    fun toLeadStatus(status: String): LeadStatus = LeadStatus.valueOf(status)
    
    @TypeConverter
    fun fromLeadPriority(priority: LeadPriority): String = priority.name
    
    @TypeConverter
    fun toLeadPriority(priority: String): LeadPriority = LeadPriority.valueOf(priority)
    
    @TypeConverter
    fun fromFollowUpType(type: FollowUpType): String = type.name
    
    @TypeConverter
    fun toFollowUpType(type: String): FollowUpType = FollowUpType.valueOf(type)
    
    @TypeConverter
    fun fromProductType(type: ProductType): String = type.name
    
    @TypeConverter
    fun toProductType(type: String): ProductType = ProductType.valueOf(type)
    
    @TypeConverter
    fun fromServiceType(type: ServiceType?): String? = type?.name
    
    @TypeConverter
    fun toServiceType(type: String?): ServiceType? = type?.let { ServiceType.valueOf(it) }
    
    @TypeConverter
    fun fromTimelineEventType(type: TimelineEventType): String = type.name
    
    @TypeConverter
    fun toTimelineEventType(type: String): TimelineEventType = TimelineEventType.valueOf(type)
    
    @TypeConverter
    fun fromNoteType(type: NoteType): String = type.name
    
    @TypeConverter
    fun toNoteType(type: String): NoteType = NoteType.valueOf(type)
    
    @TypeConverter
    fun fromNotePriority(priority: NotePriority): String = priority.name
    
    @TypeConverter
    fun toNotePriority(priority: String): NotePriority = NotePriority.valueOf(priority)
    
    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}