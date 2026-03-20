package com.message.bulksend.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CampaignDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCampaign(campaign: Campaign)

    @Update
    suspend fun updateCampaign(campaign: Campaign)

    @Query("SELECT * FROM campaigns WHERE id = :campaignId")
    suspend fun getCampaignById(campaignId: String): Campaign?

    @Query("SELECT * FROM campaigns ORDER BY timestamp DESC")
    suspend fun getAllCampaigns(): List<Campaign>

    @Query("SELECT * FROM campaigns WHERE campaignType = :campaignType ORDER BY timestamp DESC")
    suspend fun getCampaignsByType(campaignType: String): List<Campaign>

    @Query("UPDATE campaigns SET isStopped = :isStopped WHERE id = :campaignId")
    suspend fun updateStopFlag(campaignId: String, isStopped: Boolean)

    @Query("DELETE FROM campaigns WHERE id = :campaignId")
    suspend fun deleteById(campaignId: String)

    @Query("DELETE FROM campaigns WHERE id = :campaignId")
    suspend fun deleteCampaign(campaignId: String)

    @Query("UPDATE campaigns SET contactStatuses = :statuses WHERE id = :campaignId")
    suspend fun updateContactStatuses(campaignId: String, statuses: String)

    suspend fun updateContactStatus(
        campaignId: String,
        contactNumber: String,
        newStatus: String,
        failureReason: String? = null
    ) {
        val campaign = getCampaignById(campaignId)
        if (campaign != null) {
            val updatedStatuses = campaign.contactStatuses.map {
                if (it.number == contactNumber) {
                    it.copy(status = newStatus, failureReason = failureReason)
                } else {
                    it
                }
            }
            val updatedCampaign = campaign.copy(contactStatuses = updatedStatuses)
            upsertCampaign(updatedCampaign)
        }
    }

    @Query("UPDATE campaigns SET isRunning = 0 WHERE isRunning = 1")
    suspend fun recoverInterruptedCampaigns()

    @Query("SELECT * FROM campaigns WHERE groupId = :groupId AND campaignType = :campaignType ORDER BY timestamp DESC")
    suspend fun getCampaignsForGroup(groupId: String, campaignType: String): List<Campaign>

    // Gets the latest campaign for a group that might be resumable
    @Query("SELECT * FROM campaigns WHERE groupId = :groupId AND campaignType = :campaignType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getResumableCampaignForGroup(groupId: String, campaignType: String): Campaign?

    @Query("DELETE FROM campaigns")
    suspend fun deleteAllCampaigns()
}

