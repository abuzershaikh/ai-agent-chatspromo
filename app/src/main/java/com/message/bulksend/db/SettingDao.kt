package com.message.bulksend.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Data Access Object for the Setting entity.
 * Provides methods for saving and retrieving key-value settings.
 */
@Dao
interface SettingDao {

    /**
     * Inserts a new setting or updates an existing one if the key already exists.
     */
    @Upsert
    suspend fun upsertSetting(setting: Setting)

    /**
     * Retrieves a setting from the database by its unique key.
     */
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): Setting?
}
