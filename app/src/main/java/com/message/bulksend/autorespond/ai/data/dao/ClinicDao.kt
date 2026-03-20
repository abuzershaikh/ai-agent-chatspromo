package com.message.bulksend.autorespond.ai.data.dao

import androidx.room.*
import com.message.bulksend.autorespond.ai.data.model.AppointmentEntity
import com.message.bulksend.autorespond.ai.data.model.DoctorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClinicDao {
    // Doctor Operations
    @Query("SELECT * FROM doctors WHERE isEnabled = 1")
    fun getAllDoctors(): Flow<List<DoctorEntity>>
    
    @Query("SELECT * FROM doctors")
    suspend fun getAllDoctorsList(): List<DoctorEntity>

    @Query("SELECT * FROM doctors WHERE id = :id")
    suspend fun getDoctorById(id: Int): DoctorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctor(doctor: DoctorEntity): Long

    @Delete
    suspend fun deleteDoctor(doctor: DoctorEntity)

    // Appointment Operations
    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND date = :date")
    suspend fun getAppointmentsForDoctorAndDate(doctorId: Int, date: String): List<AppointmentEntity>

    @Query("SELECT COUNT(*) FROM appointments WHERE doctorId = :doctorId AND date = :date AND time = :time AND status = 'CONFIRMED'")
    suspend fun countConfirmedAppointment(doctorId: Int, date: String, time: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long
    
    @Query("SELECT * FROM appointments WHERE date = :date")
    suspend fun getAllAppointmentsForDate(date: String): List<AppointmentEntity>
    
    @Query("SELECT * FROM appointments WHERE date = :date AND status = 'CONFIRMED' ORDER BY time ASC")
    fun getConfirmedAppointmentsForDate(date: String): Flow<List<AppointmentEntity>>
    
    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND date = :date AND status = 'CONFIRMED' ORDER BY time ASC")
    fun getConfirmedAppointmentsForDoctorAndDate(doctorId: Int, date: String): Flow<List<AppointmentEntity>>
    
    @Query("SELECT * FROM appointments WHERE patientPhone = :phone AND date >= :fromDate AND status = 'CONFIRMED' ORDER BY date ASC, time ASC")
    suspend fun getUpcomingAppointmentsByPhone(phone: String, fromDate: String): List<AppointmentEntity>
    
    @Query("SELECT * FROM appointments")
    suspend fun getAllAppointments(): List<AppointmentEntity>
    
    @Query("UPDATE appointments SET status = 'CANCELLED' WHERE doctorId = :doctorId AND date = :date AND time = :time AND status = 'CONFIRMED'")
    suspend fun cancelAppointment(doctorId: Int, date: String, time: String)
}
