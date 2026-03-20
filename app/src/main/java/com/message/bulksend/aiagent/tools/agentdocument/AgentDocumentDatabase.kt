package com.message.bulksend.aiagent.tools.agentdocument

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity for Agent Document
 */
@Entity(tableName = "agent_documents")
data class AgentDocumentEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val tags: String,
    val mediaType: String,
    val filePath: String,
    val fileSize: Long,
    val mimeType: String,
    val createdAt: Long,
    val isActive: Boolean
)

/**
 * DAO for Agent Document operations
 */
@Dao
interface AgentDocumentDao {
    @Query("SELECT * FROM agent_documents WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<AgentDocumentEntity>>
    
    @Query("SELECT * FROM agent_documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: String): AgentDocumentEntity?
    
    @Query("SELECT * FROM agent_documents WHERE mediaType = :mediaType AND isActive = 1")
    fun getDocumentsByType(mediaType: String): Flow<List<AgentDocumentEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: AgentDocumentEntity)
    
    @Update
    suspend fun updateDocument(document: AgentDocumentEntity)
    
    @Query("DELETE FROM agent_documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)
    
    @Query("UPDATE agent_documents SET isActive = 0 WHERE id = :documentId")
    suspend fun deactivateDocument(documentId: String)
    
    @Query("SELECT COUNT(*) FROM agent_documents WHERE isActive = 1")
    suspend fun getDocumentCount(): Int
}

/**
 * Room Database for Agent Documents
 */
@Database(
    entities = [AgentDocumentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AgentDocumentDatabase : RoomDatabase() {
    abstract fun agentDocumentDao(): AgentDocumentDao
    
    companion object {
        @Volatile
        private var INSTANCE: AgentDocumentDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE agent_documents ADD COLUMN tags TEXT NOT NULL DEFAULT ''"
                    )
                }
            }
        
        fun getDatabase(context: Context): AgentDocumentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentDocumentDatabase::class.java,
                    "agent_document_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
