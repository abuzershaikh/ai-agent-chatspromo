package com.message.bulksend.leadmanager.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.message.bulksend.leadmanager.database.converters.Converters
import com.message.bulksend.leadmanager.database.dao.AutoAddSettingsDao
import com.message.bulksend.leadmanager.database.dao.ChatMessageDao
import com.message.bulksend.leadmanager.database.dao.CustomFieldDao
import com.message.bulksend.leadmanager.database.dao.FollowUpDao
import com.message.bulksend.leadmanager.database.dao.LeadDao
import com.message.bulksend.leadmanager.database.dao.ProductDao
import com.message.bulksend.leadmanager.database.dao.TimelineDao
import com.message.bulksend.leadmanager.notes.NoteDao
import com.message.bulksend.leadmanager.notes.NoteEntity
import com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity
import com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity
import com.message.bulksend.leadmanager.database.entities.ChatMessageEntity
import com.message.bulksend.leadmanager.database.entities.FollowUpEntity
import com.message.bulksend.leadmanager.database.entities.CustomFieldDefinitionEntity
import com.message.bulksend.leadmanager.database.entities.CustomFieldValueEntity
import com.message.bulksend.leadmanager.database.entities.LeadEntity
import com.message.bulksend.leadmanager.database.entities.ProductEntity
import com.message.bulksend.leadmanager.database.entities.TimelineEntity
import com.message.bulksend.leadmanager.payments.database.PaymentEntity
import com.message.bulksend.leadmanager.payments.database.InvoiceEntity
import com.message.bulksend.leadmanager.payments.database.PaymentDao

@Database(
    entities = [
        LeadEntity::class,
        FollowUpEntity::class,
        ProductEntity::class,
        ChatMessageEntity::class,
        AutoAddSettingsEntity::class,
        AutoAddKeywordRuleEntity::class,
        TimelineEntity::class,
        NoteEntity::class,
        CustomFieldDefinitionEntity::class,
        CustomFieldValueEntity::class,
        PaymentEntity::class,
        InvoiceEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LeadManagerDatabase : RoomDatabase() {
    
    abstract fun leadDao(): LeadDao
    abstract fun followUpDao(): FollowUpDao
    abstract fun productDao(): ProductDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun autoAddSettingsDao(): AutoAddSettingsDao
    abstract fun timelineDao(): TimelineDao
    abstract fun noteDao(): NoteDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun paymentDao(): PaymentDao
    
    companion object {
        @Volatile
        private var INSTANCE: LeadManagerDatabase? = null
        
        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create chat_messages table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT PRIMARY KEY NOT NULL,
                        leadId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        senderPhone TEXT NOT NULL,
                        messageText TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isIncoming INTEGER NOT NULL DEFAULT 1,
                        isAutoReply INTEGER NOT NULL DEFAULT 0,
                        matchedKeyword TEXT,
                        replyType TEXT NOT NULL DEFAULT 'manual',
                        packageName TEXT NOT NULL DEFAULT '',
                        isRead INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (leadId) REFERENCES leads(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_leadId ON chat_messages(leadId)")
                
                // Create custom_field_definitions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_field_definitions (
                        id TEXT PRIMARY KEY NOT NULL,
                        fieldName TEXT NOT NULL,
                        fieldType TEXT NOT NULL DEFAULT 'TEXT',
                        isRequired INTEGER NOT NULL DEFAULT 0,
                        defaultValue TEXT NOT NULL DEFAULT '',
                        options TEXT NOT NULL DEFAULT '',
                        displayOrder INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // Create custom_field_values table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_field_values (
                        leadId TEXT NOT NULL,
                        fieldId TEXT NOT NULL,
                        fieldValue TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY (leadId, fieldId)
                    )
                """)
                
                // Create auto_add_settings table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS auto_add_settings (
                        id TEXT PRIMARY KEY NOT NULL,
                        isAutoAddEnabled INTEGER NOT NULL DEFAULT 0,
                        autoAddAllMessages INTEGER NOT NULL DEFAULT 0,
                        keywordBasedAdd INTEGER NOT NULL DEFAULT 1,
                        keywords TEXT NOT NULL DEFAULT '',
                        defaultStatus TEXT NOT NULL DEFAULT 'NEW',
                        defaultSource TEXT NOT NULL DEFAULT 'WhatsApp',
                        defaultCategory TEXT NOT NULL DEFAULT 'AutoRespond',
                        defaultTags TEXT NOT NULL DEFAULT '',
                        excludeExistingContacts INTEGER NOT NULL DEFAULT 1,
                        notifyOnNewLead INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                
                // Create auto_add_keyword_rules table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS auto_add_keyword_rules (
                        id TEXT PRIMARY KEY NOT NULL,
                        keyword TEXT NOT NULL,
                        matchType TEXT NOT NULL DEFAULT 'CONTAINS',
                        assignStatus TEXT NOT NULL DEFAULT 'NEW',
                        assignCategory TEXT NOT NULL DEFAULT 'General',
                        assignTags TEXT NOT NULL DEFAULT '',
                        assignPriority TEXT NOT NULL DEFAULT 'MEDIUM',
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
        
        // Migration from version 2 to 3 - Add email field to leads
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // Migration from version 3 to 4 - Add country code and alternate phone fields
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN countryCode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE leads ADD COLUMN countryIso TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE leads ADD COLUMN alternatePhone TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // Migration from version 4 to 5 - Add timeline_entries table
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS timeline_entries (
                        id TEXT PRIMARY KEY NOT NULL,
                        leadId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL,
                        imageUri TEXT,
                        metadata TEXT NOT NULL DEFAULT '',
                        iconType TEXT NOT NULL DEFAULT 'default',
                        FOREIGN KEY (leadId) REFERENCES leads(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_entries_leadId ON timeline_entries(leadId)")
            }
        }
        
        // Migration from version 5 to 6 - Add leadScore field
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN leadScore INTEGER NOT NULL DEFAULT 50")
            }
        }
        
        // Migration from version 6 to 7 - Add lead_notes table
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS lead_notes (
                        id TEXT PRIMARY KEY NOT NULL,
                        leadId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        noteType TEXT NOT NULL DEFAULT 'GENERAL',
                        priority TEXT NOT NULL DEFAULT 'NORMAL',
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        createdBy TEXT NOT NULL DEFAULT 'User',
                        attachments TEXT NOT NULL DEFAULT '',
                        tags TEXT NOT NULL DEFAULT '',
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        parentNoteId TEXT,
                        FOREIGN KEY (leadId) REFERENCES leads(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_lead_notes_leadId ON lead_notes(leadId)")
            }
        }
        
        // Migration from version 7 to 8 - Remove custom fields tables
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop custom field tables (no longer needed)
                database.execSQL("DROP TABLE IF EXISTS custom_field_values")
                database.execSQL("DROP TABLE IF EXISTS custom_field_definitions")
            }
        }
        
        // Migration from version 8 to 9 - Re-add custom fields tables with proper structure
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create custom_field_definitions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_field_definitions (
                        id TEXT PRIMARY KEY NOT NULL,
                        fieldName TEXT NOT NULL,
                        fieldType TEXT NOT NULL DEFAULT 'TEXT',
                        isRequired INTEGER NOT NULL DEFAULT 0,
                        defaultValue TEXT NOT NULL DEFAULT '',
                        options TEXT NOT NULL DEFAULT '',
                        displayOrder INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // Create custom_field_values table with foreign key to leads
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_field_values (
                        leadId TEXT NOT NULL,
                        fieldId TEXT NOT NULL,
                        fieldValue TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY (leadId, fieldId),
                        FOREIGN KEY (leadId) REFERENCES leads(id) ON DELETE CASCADE
                    )
                """)
                
                // Create indices for better query performance
                database.execSQL("CREATE INDEX IF NOT EXISTS index_custom_field_values_leadId ON custom_field_values(leadId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_custom_field_values_fieldId ON custom_field_values(fieldId)")
            }
        }
        
        // Migration from version 9 to 10 - Add payments and invoices tables
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create payments table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS payments (
                        id TEXT PRIMARY KEY NOT NULL,
                        leadId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        paymentType TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (leadId) REFERENCES leads(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_payments_leadId ON payments(leadId)")
                
                // Create invoices table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS invoices (
                        id TEXT PRIMARY KEY NOT NULL,
                        leadId TEXT NOT NULL,
                        invoiceNumber TEXT NOT NULL,
                        amount REAL NOT NULL,
                        tax REAL NOT NULL DEFAULT 0,
                        totalAmount REAL NOT NULL,
                        status TEXT NOT NULL DEFAULT 'DRAFT',
                        addressTo TEXT NOT NULL DEFAULT '',
                        addressFrom TEXT NOT NULL DEFAULT '',
                        comments TEXT NOT NULL DEFAULT '',
                        items TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL,
                        dueDate INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (leadId) REFERENCES leads(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_leadId ON invoices(leadId)")
            }
        }
        
        fun getDatabase(context: Context): LeadManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LeadManagerDatabase::class.java,
                    "lead_manager_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigrationOnDowngrade()
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}