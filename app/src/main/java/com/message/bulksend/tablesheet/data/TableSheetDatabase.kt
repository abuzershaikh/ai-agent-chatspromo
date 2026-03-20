package com.message.bulksend.tablesheet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.message.bulksend.tablesheet.data.dao.*
import com.message.bulksend.tablesheet.data.models.*

@Database(
    entities = [TableModel::class, ColumnModel::class, RowModel::class, CellModel::class, FolderModel::class],
    version = 4,
    exportSchema = false
)
abstract class TableSheetDatabase : RoomDatabase() {
    
    abstract fun tableDao(): TableDao
    abstract fun columnDao(): ColumnDao
    abstract fun rowDao(): RowDao
    abstract fun cellDao(): CellDao
    abstract fun folderDao(): FolderDao
    
    companion object {
        @Volatile
        private var INSTANCE: TableSheetDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tables ADD COLUMN tags TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE tables ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tables ADD COLUMN folderId INTEGER DEFAULT NULL")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '#1976D2'
                    )
                """.trimIndent())
            }
        }
        
        fun getDatabase(context: Context): TableSheetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TableSheetDatabase::class.java,
                    "tablesheet_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
