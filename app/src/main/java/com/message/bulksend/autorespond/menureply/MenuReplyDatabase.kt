package com.message.bulksend.autorespond.menureply

import androidx.room.*
import android.content.Context

/**
 * DAO for Menu Items
 */
@Dao
interface MenuItemDao {
    @Query("SELECT * FROM menu_items WHERE parentId IS NULL ORDER BY orderIndex ASC")
    suspend fun getRootMenuItems(): List<MenuItem>
    
    @Query("SELECT * FROM menu_items WHERE parentId = :parentId ORDER BY orderIndex ASC")
    suspend fun getChildrenByParentId(parentId: String): List<MenuItem>
    
    @Query("SELECT * FROM menu_items WHERE id = :itemId")
    suspend fun getMenuItemById(itemId: String): MenuItem?
    
    @Query("SELECT * FROM menu_items ORDER BY orderIndex ASC")
    suspend fun getAllMenuItems(): List<MenuItem>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItem(item: MenuItem)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItems(items: List<MenuItem>)
    
    @Update
    suspend fun updateMenuItem(item: MenuItem)
    
    @Query("DELETE FROM menu_items WHERE id = :itemId")
    suspend fun deleteMenuItem(itemId: String)
    
    @Query("DELETE FROM menu_items")
    suspend fun deleteAllMenuItems()
}

/**
 * DAO for Menu Configuration
 */
@Dao
interface MenuConfigDao {
    @Query("SELECT * FROM menu_reply_config WHERE id = 'default'")
    suspend fun getConfig(): MenuReplyConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: MenuReplyConfig)
    
    @Update
    suspend fun updateConfig(config: MenuReplyConfig)
}

/**
 * DAO for User Menu Context - Tracks each user's position in menu
 */
@Dao
interface UserMenuContextDao {
    @Query("SELECT * FROM user_menu_context WHERE userId = :userId")
    suspend fun getUserContext(userId: String): UserMenuContext?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContext(context: UserMenuContext)
    
    @Query("UPDATE user_menu_context SET currentParentId = :parentId, breadcrumb = :breadcrumb, lastInteractionTime = :time WHERE userId = :userId")
    suspend fun updateContext(userId: String, parentId: String?, breadcrumb: String, time: Long)
    
    @Query("DELETE FROM user_menu_context WHERE userId = :userId")
    suspend fun deleteUserContext(userId: String)
    
    @Query("DELETE FROM user_menu_context WHERE isActive = 0 AND requiresKeywordRestart = 0 AND lastInteractionTime < :expiryTime")
    suspend fun deleteInactiveContexts(expiryTime: Long)
    
    @Query("UPDATE user_menu_context SET isActive = 0, currentParentId = NULL, breadcrumb = '', lastInteractionTime = :time, requiresKeywordRestart = 0 WHERE userId = :userId")
    suspend fun resetUserContext(userId: String, time: Long)
    
    @Query("UPDATE user_menu_context SET isActive = 0, currentParentId = NULL, breadcrumb = '', lastInteractionTime = :time, requiresKeywordRestart = 1 WHERE userId = :userId")
    suspend fun markContextTimedOut(userId: String, time: Long)
    
    @Query("SELECT * FROM user_menu_context WHERE isActive = 1")
    suspend fun getAllActiveContexts(): List<UserMenuContext>
}

/**
 * Room Database for Menu Reply
 */
@Database(
    entities = [MenuItem::class, MenuReplyConfig::class, UserMenuContext::class],
    version = 4,
    exportSchema = false
)
abstract class MenuReplyDatabase : RoomDatabase() {
    abstract fun menuItemDao(): MenuItemDao
    abstract fun menuConfigDao(): MenuConfigDao
    abstract fun userMenuContextDao(): UserMenuContextDao
    
    companion object {
        @Volatile
        private var INSTANCE: MenuReplyDatabase? = null
        
        fun getDatabase(context: Context): MenuReplyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MenuReplyDatabase::class.java,
                    "menu_reply_database"
                )
                .fallbackToDestructiveMigration() // For development - handles version changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
