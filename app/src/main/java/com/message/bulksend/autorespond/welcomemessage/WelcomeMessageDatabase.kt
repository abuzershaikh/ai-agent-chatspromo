package com.message.bulksend.autorespond.welcomemessage

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DAO for Welcome Messages
 */
@Dao
interface WelcomeMessageDao {
    @Query("SELECT * FROM welcome_messages WHERE isEnabled = 1 ORDER BY orderIndex ASC")
    suspend fun getEnabledMessages(): List<WelcomeMessage>
    
    @Query("SELECT * FROM welcome_messages ORDER BY orderIndex ASC")
    suspend fun getAllMessages(): List<WelcomeMessage>
    
    @Query("SELECT * FROM welcome_messages WHERE id = :id")
    suspend fun getMessageById(id: Int): WelcomeMessage?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: WelcomeMessage): Long
    
    @Update
    suspend fun updateMessage(message: WelcomeMessage)
    
    @Delete
    suspend fun deleteMessage(message: WelcomeMessage)
    
    @Query("DELETE FROM welcome_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Int)
    
    @Query("DELETE FROM welcome_messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT MAX(orderIndex) FROM welcome_messages")
    suspend fun getMaxOrderIndex(): Int?
}

/**
 * DAO for tracking sent welcome messages
 */
@Dao
interface WelcomeMessageSentDao {
    @Query("SELECT * FROM welcome_message_sent WHERE oderId = :oderId")
    suspend fun getSentRecord(oderId: String): WelcomeMessageSent?
    
    @Query("SELECT EXISTS(SELECT 1 FROM welcome_message_sent WHERE oderId = :oderId)")
    suspend fun hasReceivedWelcome(oderId: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentRecord(record: WelcomeMessageSent)
    
    @Query("UPDATE welcome_message_sent SET messageCount = messageCount + 1 WHERE oderId = :oderId")
    suspend fun incrementMessageCount(oderId: String)
    
    @Query("DELETE FROM welcome_message_sent WHERE oderId = :oderId")
    suspend fun deleteSentRecord(oderId: String)
    
    @Query("DELETE FROM welcome_message_sent")
    suspend fun deleteAllSentRecords()
    
    @Query("SELECT COUNT(*) FROM welcome_message_sent")
    suspend fun getTotalSentCount(): Int
}

/**
 * Room Database for Welcome Messages
 */
@Database(
    entities = [WelcomeMessage::class, WelcomeMessageSent::class],
    version = 2,
    exportSchema = false
)
abstract class WelcomeMessageDatabase : RoomDatabase() {
    abstract fun welcomeMessageDao(): WelcomeMessageDao
    abstract fun welcomeMessageSentDao(): WelcomeMessageSentDao
    
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE welcome_messages ADD COLUMN selectedDocumentsJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Volatile
        private var INSTANCE: WelcomeMessageDatabase? = null
        
        fun getDatabase(context: Context): WelcomeMessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WelcomeMessageDatabase::class.java,
                    "welcome_message_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
