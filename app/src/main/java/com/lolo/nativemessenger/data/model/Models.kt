package com.lolo.nativemessenger.data.model

data class Conversation(
    val id: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
)

data class Message(
    val id: String,
    val conversationId: String,
    val text: String,
    val isMine: Boolean,
    val timestamp: Long,
)
