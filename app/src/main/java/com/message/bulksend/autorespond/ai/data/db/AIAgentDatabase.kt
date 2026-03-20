package com.message.bulksend.autorespond.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.message.bulksend.autorespond.ai.data.dao.UserProfileDao
import com.message.bulksend.autorespond.ai.data.model.UserProfile
// Note: Product is in a separate DB for now, or we can merge. Let's keep separate 
// to avoid migration complexities with existing DBs unless necessary.
// Actually, to keep it clean, let's put UserProfile in its own DB for modularity.

@Database(entities = [UserProfile::class], version = 2, exportSchema = false)
abstract class AIAgentDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AIAgentDatabase? = null

        fun getDatabase(context: Context): AIAgentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AIAgentDatabase::class.java,
                    "ai_agent_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
