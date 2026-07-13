package com.cslearningos.mobile.feature.assistant.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assistant_conversations")
data class AssistantConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "messages_json") val messagesJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
