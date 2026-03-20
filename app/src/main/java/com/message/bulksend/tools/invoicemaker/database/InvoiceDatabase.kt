package com.message.bulksend.tools.invoicemaker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [InvoiceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class InvoiceDatabase : RoomDatabase() {
    
    abstract fun invoiceDao(): InvoiceDao
    
    companion object {
        @Volatile
        private var INSTANCE: InvoiceDatabase? = null
        
        fun getDatabase(context: Context): InvoiceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InvoiceDatabase::class.java,
                    "invoice_maker_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
