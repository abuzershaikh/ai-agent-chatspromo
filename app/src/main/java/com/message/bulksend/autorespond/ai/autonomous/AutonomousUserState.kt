package com.message.bulksend.autorespond.ai.autonomous

data class AutonomousUserState(
    val senderPhone: String,
    val senderName: String,
    val lastInboundAt: Long = 0L,
    val lastOutboundAt: Long = 0L,
    val lastAutonomousAt: Long = 0L,
    val nudgesToday: Int = 0,
    val nudgeDayKey: String = "",
    val pauseUntil: Long = 0L,
    val lastStateHash: String = "",
    val lastMessageHash: String = "",
    val lastMessageAt: Long = 0L,
    val updatedAt: Long = 0L
)
