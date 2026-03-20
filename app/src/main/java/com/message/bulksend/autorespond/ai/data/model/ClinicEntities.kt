package com.message.bulksend.autorespond.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val specialty: String,
    val slotDurationMinutes: Int = 30,
    val startTime: String = "09:00",      // HH:mm (24h internal)
    val endTime: String = "17:00",        // HH:mm (24h internal)
    val lunchStartTime: String = "13:00", // HH:mm (24h internal)
    val lunchEndTime: String = "14:00",   // HH:mm (24h internal)
    val isEnabled: Boolean = true
)

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val doctorId: Int,
    val patientName: String,
    val patientPhone: String?,
    val date: String,  // YYYY-MM-DD
    val time: String,  // HH:mm (24h internal)
    val status: String = "CONFIRMED", // CONFIRMED, CANCELLED
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
