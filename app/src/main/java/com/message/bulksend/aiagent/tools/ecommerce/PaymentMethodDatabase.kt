package com.message.bulksend.aiagent.tools.ecommerce

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity for Payment Method
 */
@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    
    // QR Code
    val qrCodeType: String?,
    val qrCodeImagePath: String?,
    val fixedPrice: Double?,
    val agentPriceField: String?,
    
    // UPI
    val upiId: String?,
    
    // Razorpay
    val razorpayKeyId: String?,
    val razorpayKeySecret: String?,
    val razorpayWebhookSecret: String?,
    
    // PayPal
    val paypalEmail: String?,
    val paypalClientId: String?,
    val paypalClientSecret: String?,
    
    // Custom Group
    val customGroupName: String?,
    val customFieldsJson: String? // JSON string of List<CustomField>
)

/**
 * DAO for Payment Methods
 */
@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY createdAt DESC")
    fun getAllPaymentMethods(): Flow<List<PaymentMethodEntity>>
    
    @Query("SELECT * FROM payment_methods WHERE isEnabled = 1")
    fun getEnabledPaymentMethods(): Flow<List<PaymentMethodEntity>>
    
    @Query("SELECT * FROM payment_methods WHERE isEnabled = 1")
    suspend fun getEnabledPaymentMethodsDirect(): List<PaymentMethodEntity>
    
    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun getPaymentMethodById(id: String): PaymentMethodEntity?
    
    @Query("SELECT * FROM payment_methods WHERE type = :type")
    fun getPaymentMethodsByType(type: String): Flow<List<PaymentMethodEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethod(method: PaymentMethodEntity)
    
    @Update
    suspend fun updatePaymentMethod(method: PaymentMethodEntity)
    
    @Query("DELETE FROM payment_methods WHERE id = :id")
    suspend fun deletePaymentMethod(id: String)
    
    @Query("UPDATE payment_methods SET isEnabled = :enabled WHERE id = :id")
    suspend fun togglePaymentMethod(id: String, enabled: Boolean)
    
    @Query("SELECT COUNT(*) FROM payment_methods WHERE isEnabled = 1")
    suspend fun getEnabledCount(): Int
}

/**
 * Room Database for Payment Methods
 */
@Database(
    entities = [PaymentMethodEntity::class],
    version = 3,
    exportSchema = false
)
abstract class PaymentMethodDatabase : RoomDatabase() {
    abstract fun paymentMethodDao(): PaymentMethodDao
    
    companion object {
        @Volatile
        private var INSTANCE: PaymentMethodDatabase? = null
        
        fun getDatabase(context: Context): PaymentMethodDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaymentMethodDatabase::class.java,
                    "payment_method_database"
                )
                .fallbackToDestructiveMigration() // Auto-migrate by destroying old data
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
