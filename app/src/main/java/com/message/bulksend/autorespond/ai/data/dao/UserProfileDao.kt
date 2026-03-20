package com.message.bulksend.autorespond.ai.data.dao

import androidx.room.*
import com.message.bulksend.autorespond.ai.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE phoneNumber = :phoneNumber")
    suspend fun getUserProfile(phoneNumber: String): UserProfile?

    @Query("SELECT * FROM user_profiles")
    fun getAllUserProfiles(): Flow<List<UserProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(userProfile: UserProfile)

    @Update
    suspend fun updateUserProfile(userProfile: UserProfile)

    @Query("UPDATE user_profiles SET name = :name, updatedAt = :time WHERE phoneNumber = :phoneNumber")
    suspend fun updateUserName(phoneNumber: String, name: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET currentIntent = :intent, updatedAt = :time WHERE phoneNumber = :phoneNumber")
    suspend fun updateUserIntent(phoneNumber: String, intent: String, time: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteUserProfile(userProfile: UserProfile)
}
