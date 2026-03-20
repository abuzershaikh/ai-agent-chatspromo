package com.message.bulksend.bulksend

import android.content.Context
import com.message.bulksend.bulksend.textcamp.AutonomousExecutionStats
import com.message.bulksend.bulksend.textcamp.recommendedAutonomousDays
import com.message.bulksend.bulksend.textcamp.startAndEndOfToday
import com.message.bulksend.db.AutonomousSendQueueDao
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.CampaignDao
import com.message.bulksend.utils.CampaignAutoSendManager

const val AUTONOMOUS_CAMPAIGN_TYPE = "BULKTEXT_AUTONOMOUS"

enum class AutonomousCampaignPhase {
    RUNNING,
    PAUSED,
    PENDING,
    COMPLETED
}

data class AutonomousCampaignProgressState(
    val campaign: Campaign,
    val stats: AutonomousExecutionStats,
    val phase: AutonomousCampaignPhase,
    val selectedDays: Int,
    val runtimeConfig: AutonomousCampaignRuntimeConfig?
) {
    val queuedCount: Int
        get() = stats.queued

    val sentCount: Int
        get() = campaign.sentCount

    val failedCount: Int
        get() = campaign.failedCount.coerceAtLeast(stats.failed)

    val pendingCount: Int
        get() = campaign.contactStatuses.count { it.status == "pending" }

    val completedCount: Int
        get() = (sentCount + failedCount).coerceAtMost(campaign.totalContacts)

    val recommendedDays: Int
        get() = recommendedAutonomousDays(campaign.totalContacts)

    val progressFraction: Float
        get() = if (campaign.totalContacts <= 0) {
            0f
        } else {
            (completedCount.toFloat() / campaign.totalContacts).coerceIn(0f, 1f)
        }
}

suspend fun loadAutonomousExecutionStatsForAutonomousCampaign(
    dao: AutonomousSendQueueDao,
    campaignId: String,
    pauseReason: String?
): AutonomousExecutionStats {
    val (dayStart, dayEnd) = startAndEndOfToday()
    val queuedEntries = dao.getQueuedForCampaign(campaignId)
    val sentToday = dao.getSentTodayCount(campaignId, dayStart, dayEnd)
    val queuedToday = dao.getQueuedTodayCount(campaignId, dayStart, dayEnd)
    val failed = dao.countByStatus(campaignId, "failed")
    val nextSend = dao.getNextSendTime(campaignId)
    val remainingDays = queuedEntries.map { it.dayIndex }.distinct().count()
    return AutonomousExecutionStats(
        sentToday = sentToday,
        queuedToday = queuedToday,
        queued = queuedEntries.size,
        failed = failed,
        remainingDays = remainingDays,
        nextSendTimeMillis = nextSend,
        autoPauseReason = pauseReason
    )
}

suspend fun loadAutonomousCampaignProgressState(
    context: Context,
    campaignDao: CampaignDao,
    autonomousQueueDao: AutonomousSendQueueDao,
    campaignId: String
): AutonomousCampaignProgressState? {
    if (campaignId.isBlank()) return null

    val campaign = campaignDao.getCampaignById(campaignId) ?: return null
    if (campaign.campaignType != AUTONOMOUS_CAMPAIGN_TYPE) return null

    val allEntries = autonomousQueueDao.getAllForCampaign(campaignId)
    val queuedCount = allEntries.count { it.status == "queued" }
    val phase = when {
        campaign.isRunning -> AutonomousCampaignPhase.RUNNING
        campaign.isStopped && queuedCount > 0 -> AutonomousCampaignPhase.PAUSED
        queuedCount > 0 -> AutonomousCampaignPhase.PENDING
        else -> AutonomousCampaignPhase.COMPLETED
    }
    val pauseReason = if (phase == AutonomousCampaignPhase.PAUSED) {
        "Campaign is paused."
    } else {
        null
    }
    val selectedDays = allEntries.map { it.dayIndex }.distinct().count().let { dayCount ->
        when {
            dayCount > 0 -> dayCount
            campaign.totalContacts > 0 -> 1
            else -> 0
        }
    }

    return AutonomousCampaignProgressState(
        campaign = campaign,
        stats = loadAutonomousExecutionStatsForAutonomousCampaign(
            dao = autonomousQueueDao,
            campaignId = campaignId,
            pauseReason = pauseReason
        ),
        phase = phase,
        selectedDays = selectedDays,
        runtimeConfig = AutonomousCampaignConfigStore.getConfig(context, campaignId)
    )
}

suspend fun resetAutonomousCampaign(
    context: Context,
    campaignDao: CampaignDao,
    autonomousQueueDao: AutonomousSendQueueDao,
    campaign: Campaign
): Campaign {
    val resetCampaign = campaign.copy(
        isRunning = false,
        isStopped = true,
        contactStatuses = campaign.contactStatuses.map {
            it.copy(status = "pending", failureReason = null)
        }
    )
    AutonomousCampaignScheduler(context).cancel(resetCampaign.id)
    autonomousQueueDao.deleteForCampaign(resetCampaign.id)
    campaignDao.upsertCampaign(resetCampaign)
    AutonomousCampaignConfigStore.clearConfig(context, resetCampaign.id)
    CampaignAutoSendManager.onCampaignStopped(resetCampaign)
    return resetCampaign
}
