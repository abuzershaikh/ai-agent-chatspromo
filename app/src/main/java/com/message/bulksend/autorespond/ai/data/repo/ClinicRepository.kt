package com.message.bulksend.autorespond.ai.data.repo

import android.content.Context
import com.message.bulksend.autorespond.ai.data.model.AppointmentEntity
import com.message.bulksend.autorespond.ai.data.model.DoctorEntity
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ClinicRepository(private val context: Context) {
    
    private val messageDb = MessageDatabase.getDatabase(context)
    private val clinicDao = messageDb.clinicDao()
    private val settingsManager = AIAgentSettingsManager(context)
    
    // TableSheet DB
    private val tableSheetDb = TableSheetDatabase.getDatabase(context)
    private val folderDao = tableSheetDb.folderDao()
    private val tableDao = tableSheetDb.tableDao()
    private val columnDao = tableSheetDb.columnDao()
    private val rowDao = tableSheetDb.rowDao()
    private val cellDao = tableSheetDb.cellDao()
    
    // ─── Doctor Operations ───
    fun getAllDoctors(): Flow<List<DoctorEntity>> = clinicDao.getAllDoctors()
    
    suspend fun saveDoctor(doctor: DoctorEntity) = withContext(Dispatchers.IO) {
        clinicDao.insertDoctor(doctor)
        syncDoctorsToSheet()
    }
    
    suspend fun deleteDoctor(doctor: DoctorEntity) = withContext(Dispatchers.IO) {
        clinicDao.deleteDoctor(doctor)
        syncDoctorsToSheet()
    }
    
    // ─── Appointment Operations ───
    suspend fun getAppointments(doctorId: Int, date: String): List<AppointmentEntity> = withContext(Dispatchers.IO) {
        clinicDao.getAppointmentsForDoctorAndDate(doctorId, date)
    }
    
    suspend fun addBooking(appointment: AppointmentEntity): Boolean = withContext(Dispatchers.IO) {
        // Duplicate check — skip if same doctor+date+time already CONFIRMED
        val existing = clinicDao.countConfirmedAppointment(appointment.doctorId, appointment.date, appointment.time)
        if (existing > 0) {
            android.util.Log.w("ClinicRepo", "⚠️ Duplicate booking blocked: Dr=${appointment.doctorId} ${appointment.date} ${appointment.time}")
            return@withContext false
        }
        clinicDao.insertAppointment(appointment)
        // Sync to per-doctor daily sheet
        val doctor = clinicDao.getDoctorById(appointment.doctorId)
        if (doctor != null) {
            syncDoctorDailySheet(doctor, appointment.date)
        }
        true
    }
    
    suspend fun cancelBooking(doctorId: Int, date: String, time: String) = withContext(Dispatchers.IO) {
        clinicDao.cancelAppointment(doctorId, date, time)
        val doctor = clinicDao.getDoctorById(doctorId)
        if (doctor != null) {
            syncDoctorDailySheet(doctor, date)
        }
    }
    
    suspend fun getAllDoctorsList(): List<DoctorEntity> = clinicDao.getAllDoctorsList()
    
    fun getConfirmedAppointmentsFlow(date: String): Flow<List<AppointmentEntity>> = clinicDao.getConfirmedAppointmentsForDate(date)
    
    fun getConfirmedAppointmentsForDoctorFlow(doctorId: Int, date: String): Flow<List<AppointmentEntity>> = clinicDao.getConfirmedAppointmentsForDoctorAndDate(doctorId, date)
    
    suspend fun getUpcomingAppointmentsByPhone(phone: String): List<AppointmentEntity> = withContext(Dispatchers.IO) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        clinicDao.getUpcomingAppointmentsByPhone(phone, today)
    }
    
    suspend fun getAllAppointmentsForDate(date: String): List<AppointmentEntity> = withContext(Dispatchers.IO) {
        clinicDao.getAllAppointmentsForDate(date)
    }
    
    // ─── SLOT ENGINE ───
    
    /**
     * Generate all possible time slots for a doctor.
     * Respects: doctor hours, lunch break, AND clinic hours.
     */
    fun generateAllSlots(doctor: DoctorEntity): List<String> {
        val slots = mutableListOf<String>()
        val duration = doctor.slotDurationMinutes
        if (duration <= 0) return slots
        
        // Doctor's own hours
        val docStartMin = timeToMinutes(doctor.startTime)
        val docEndMin = timeToMinutes(doctor.endTime)
        
        // Clinic hours cap
        val clinicOpenMin = timeToMinutes(settingsManager.clinicOpenTime)
        val clinicCloseMin = timeToMinutes(settingsManager.clinicCloseTime)
        
        // Effective range = intersection of doctor hours and clinic hours
        val startMin = maxOf(docStartMin, clinicOpenMin)
        val endMin = minOf(docEndMin, clinicCloseMin)
        
        val lunchStartMin = timeToMinutes(doctor.lunchStartTime)
        val lunchEndMin = timeToMinutes(doctor.lunchEndTime)
        
        var t = startMin
        while (t + duration <= endMin) {
            // Skip if slot overlaps with lunch
            if (t < lunchEndMin && t + duration > lunchStartMin) {
                t = lunchEndMin
                continue
            }
            slots.add(minutesToTime(t))
            t += duration
        }
        return slots
    }
    
    /**
     * Get available (unbooked) slots for a doctor on a specific date.
     */
    suspend fun getAvailableSlots(doctorId: Int, date: String): List<String> = withContext(Dispatchers.IO) {
        val doctor = clinicDao.getDoctorById(doctorId) ?: return@withContext emptyList()
        val allSlots = generateAllSlots(doctor)
        val booked = clinicDao.getAppointmentsForDoctorAndDate(doctorId, date)
            .filter { it.status == "CONFIRMED" }
            .map { it.time }
        allSlots.filter { it !in booked }
    }
    
    private fun timeToMinutes(time24: String): Int {
        val parts = time24.split(":")
        return (parts[0].toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    
    private fun minutesToTime(minutes: Int): String {
        return "%02d:%02d".format(minutes / 60, minutes % 60)
    }
    
    private fun to12h(time24: String): String {
        val parts = time24.split(":")
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts.getOrNull(1) ?: "00"
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return "$h12:$m $amPm"
    }
    
    // ─── SYNC: DOCTORS ───
    private suspend fun syncDoctorsToSheet() {
        val folderId = ensureClinicFolder()
        val tableId = ensureDoctorsTable(folderId)
        val doctors = clinicDao.getAllDoctorsList()
        val columns = columnDao.getColumnsByTableIdSync(tableId)
        
        val colName = columns.find { it.name == "Name" }?.id
        val colSpecialty = columns.find { it.name == "Specialty" }?.id
        val colDuration = columns.find { it.name == "Slot Duration" }?.id
        val colStart = columns.find { it.name == "Available From" }?.id
        val colEnd = columns.find { it.name == "Available To" }?.id
        val colLunchStart = columns.find { it.name == "Lunch From" }?.id
        val colLunchEnd = columns.find { it.name == "Lunch To" }?.id
        val colSlots = columns.find { it.name == "Total Slots" }?.id
        
        rowDao.deleteRowsByTableId(tableId)
        tableDao.updateRowCount(tableId, doctors.size)
        
        doctors.forEachIndexed { index, doctor ->
            val rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = index))
            val totalSlots = generateAllSlots(doctor).size
            
            if (colName != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colName, value = doctor.name))
            if (colSpecialty != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colSpecialty, value = doctor.specialty))
            if (colDuration != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colDuration, value = "${doctor.slotDurationMinutes} mins"))
            if (colStart != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colStart, value = to12h(doctor.startTime)))
            if (colEnd != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colEnd, value = to12h(doctor.endTime)))
            if (colLunchStart != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colLunchStart, value = to12h(doctor.lunchStartTime)))
            if (colLunchEnd != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colLunchEnd, value = to12h(doctor.lunchEndTime)))
            if (colSlots != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colSlots, value = "$totalSlots"))
        }
    }
    
    // ─── SYNC: PER-DOCTOR DAILY APPOINTMENT SHEET ───
    /**
     * Creates/updates a sheet per doctor per day.
     * Sheet name: "Dr. Jamil 2026-02-13"
     */
    private suspend fun syncDoctorDailySheet(doctor: DoctorEntity, date: String) {
        val folderId = ensureClinicFolder()
        val sheetName = "Dr. ${doctor.name} $date"
        val tableId = ensureDoctorDailySheet(folderId, sheetName)
        
        val bookings = clinicDao.getAppointmentsForDoctorAndDate(doctor.id, date)
        val columns = columnDao.getColumnsByTableIdSync(tableId)
        
        val colPatient = columns.find { it.name == "Patient Name" }?.id
        val colPhone = columns.find { it.name == "Phone" }?.id
        val colTime = columns.find { it.name == "Time" }?.id
        val colStatus = columns.find { it.name == "Status" }?.id
        
        rowDao.deleteRowsByTableId(tableId)
        tableDao.updateRowCount(tableId, bookings.size)
        
        bookings.forEachIndexed { index, booking ->
            val rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = index))
            
            if (colPatient != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colPatient, value = booking.patientName))
            if (colPhone != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colPhone, value = booking.patientPhone ?: ""))
            if (colTime != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colTime, value = to12h(booking.time)))
            if (colStatus != null) cellDao.insertCell(CellModel(rowId = rowId, columnId = colStatus, value = booking.status))
        }
    }
    
    // ─── TABLE HELPERS ───
    
    private suspend fun ensureClinicFolder(): Long {
        val folder = folderDao.getFolderByName("Clinic Automation")
        if (folder == null) {
            return folderDao.insertFolder(FolderModel(name = "Clinic Automation", colorHex = "#EF4444"))
        }
        return folder.id
    }
    
    private suspend fun ensureDoctorsTable(folderId: Long): Long {
        val table = tableDao.getTablesByFolderIdSync(folderId).find { it.name == "Doctors" }
        if (table == null) {
            val tableId = tableDao.insertTable(TableModel(name = "Doctors", folderId = folderId, columnCount = 8))
            
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Name", type = "STRING", orderIndex = 0))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Specialty", type = "STRING", orderIndex = 1))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Slot Duration", type = "STRING", orderIndex = 2))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Available From", type = "STRING", orderIndex = 3))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Available To", type = "STRING", orderIndex = 4))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Lunch From", type = "STRING", orderIndex = 5))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Lunch To", type = "STRING", orderIndex = 6))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Total Slots", type = "STRING", orderIndex = 7))
            
            return tableId
        }
        return table.id
    }
    
    /**
     * Creates a per-doctor daily appointment sheet.
     * Name format: "Dr. Jamil 2026-02-13"
     */
    private suspend fun ensureDoctorDailySheet(folderId: Long, sheetName: String): Long {
        val table = tableDao.getTablesByFolderIdSync(folderId).find { it.name == sheetName }
        if (table == null) {
            val tableId = tableDao.insertTable(TableModel(name = sheetName, folderId = folderId, columnCount = 4))
            
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Patient Name", type = "STRING", orderIndex = 0))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Phone", type = "PHONE", orderIndex = 1))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Time", type = "STRING", orderIndex = 2))
            columnDao.insertColumn(ColumnModel(tableId = tableId, name = "Status", type = "SELECT", orderIndex = 3, selectOptions = "[\"CONFIRMED\",\"CANCELLED\"]"))
            
            return tableId
        }
        return table.id
    }
}
