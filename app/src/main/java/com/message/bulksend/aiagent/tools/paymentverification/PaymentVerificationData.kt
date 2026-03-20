package com.message.bulksend.aiagent.tools.paymentverification

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Payment Verification Entity
 */
@Entity(tableName = "payment_verifications")
data class PaymentVerification(
    @PrimaryKey
    val id: String = "", // customerPhone_timestamp
    val customerPhone: String = "",
    val orderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val recommendation: String = "", // PAID, MANUAL_REVIEW, REJECTED
    val isFake: Boolean = false,
    val confidence: Int = 0,
    
    // Payment Details
    val upiId: String = "",
    val amount: Double = 0.0,
    val paymentDate: String = "",
    val paymentTime: String = "",
    val transactionId: String = "",
    val payeeName: String = "",
    val payerName: String = "",
    val paymentStatus: String = "",
    
    // Expected Details (from app)
    val expectedName: String = "",
    val expectedUpiId: String = "",
    val expectedAmount: Double = 0.0,
    
    // Match Results
    val nameMatched: Boolean = false,
    val upiMatched: Boolean = false,
    val amountMatched: Boolean = false,
    
    // Custom Fields (JSON string)
    val customFieldsExtracted: String = "", // {"Bank Name": "HDFC Bank", "IFSC Code": "HDFC0001234"}
    val customFieldsExpected: String = "",  // {"Bank Name": "HDFC Bank", "IFSC Code": "HDFC0001234"}
    val customFieldsMatched: Boolean = false,
    
    // Metadata
    val screenshotTimestamp: String = "",
    val uploadTimestamp: String = "",
    val timeDifferenceMinutes: Double = 0.0,
    val isWithinTimeLimit: Boolean = false,
    
    // AI Analysis
    val reasoning: String = "",
    val geminiRawResponse: String = "", // Complete raw response from Gemini AI
    
    // Status tracking
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, PROCESSED
    val processedAt: Long = 0,
    val notes: String = ""
)

/**
 * DAO for Payment Verifications
 */
@Dao
interface PaymentVerificationDao {
    @Query("SELECT * FROM payment_verifications ORDER BY timestamp DESC")
    fun getAllVerifications(): Flow<List<PaymentVerification>>

    @Query("SELECT * FROM payment_verifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentVerification?

    @Query("SELECT * FROM payment_verifications WHERE status = 'PENDING' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPending(): PaymentVerification?

    @Query("SELECT * FROM payment_verifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentVerifications(limit: Int = 200): List<PaymentVerification>
    
    @Query("SELECT * FROM payment_verifications WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingVerifications(): Flow<List<PaymentVerification>>
    
    @Query("SELECT * FROM payment_verifications WHERE recommendation = :recommendation ORDER BY timestamp DESC")
    fun getByRecommendation(recommendation: String): Flow<List<PaymentVerification>>
    
    @Query("SELECT * FROM payment_verifications WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getByCustomerPhone(phone: String): Flow<List<PaymentVerification>>
    
    @Query("SELECT * FROM payment_verifications WHERE orderId = :orderId")
    suspend fun getByOrderId(orderId: String): PaymentVerification?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(verification: PaymentVerification)
    
    @Update
    suspend fun update(verification: PaymentVerification)
    
    @Query("UPDATE payment_verifications SET status = :status, processedAt = :processedAt, notes = :notes WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, processedAt: Long, notes: String)
    
    @Query("DELETE FROM payment_verifications WHERE timestamp < :cutoffTime")
    suspend fun deleteOldVerifications(cutoffTime: Long)
    
    @Query("SELECT COUNT(*) FROM payment_verifications WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}

/**
 * Database for Payment Verifications
 */
@Database(
    entities = [PaymentVerification::class],
    version = 4,
    exportSchema = false
)
abstract class PaymentVerificationDatabase : RoomDatabase() {
    abstract fun verificationDao(): PaymentVerificationDao
    
    companion object {
        @Volatile
        private var INSTANCE: PaymentVerificationDatabase? = null
        
        fun getDatabase(context: android.content.Context): PaymentVerificationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaymentVerificationDatabase::class.java,
                    "payment_verification_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
