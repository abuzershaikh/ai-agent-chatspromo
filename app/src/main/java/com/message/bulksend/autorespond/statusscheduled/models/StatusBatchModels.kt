package com.message.bulksend.autorespond.statusscheduled.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class MediaType {
    IMAGE, VIDEO
}

enum class ScheduleType {
    AUTO, MANUAL
}

enum class BatchStatus {
    DRAFT, SCHEDULED, POSTING, POSTED, FAILED
}

data class MediaItem(
    val uri: String,
    val type: MediaType,
    val name: String,
    val size: Long,
    val duration: Long? = null,
    val delayMinutes: Int = 0 // Delay before posting this item
)

@Entity(tableName = "status_batches")
@TypeConverters(StatusBatchConverters::class)
data class StatusBatch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaList: List<MediaItem>,
    val scheduleType: ScheduleType,
    val startDate: Long? = null,
    val time: String? = null, // Format: "HH:mm"
    val amPm: String? = null, // "AM" or "PM"
    val repeatDaily: Boolean = false,
    val reminderMinutes: Int? = null,
    val status: BatchStatus = BatchStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long? = null
)

class StatusBatchConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromMediaList(value: List<MediaItem>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toMediaList(value: String): List<MediaItem> {
        val type = object : TypeToken<List<MediaItem>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun fromBatchStatus(value: BatchStatus): String = value.name

    @TypeConverter
    fun toBatchStatus(value: String): BatchStatus = BatchStatus.valueOf(value)
}
