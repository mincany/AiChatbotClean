package com.example.chatbot.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;

@DynamoDbBean
public class Conversation {
    private String conversationId;
    private String userId;
    private String lastMessageId;
    private String lastMessageTime;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Conversation() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "open";
    }

    public Conversation(String conversationId, String userId) {
        this();
        this.conversationId = conversationId;
        this.userId = userId;
        this.lastMessageTime = Instant.now().toString();
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("ConversationId")
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @DynamoDbSortKey
    @DynamoDbSecondarySortKey(indexNames = {"UserId-LastMessageTime-index"})
    @DynamoDbAttribute("LastMessageTime")
    public String getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(String lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"UserId-LastMessageTime-index"})
    @DynamoDbAttribute("UserId")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(String lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper method to update last message info
    public void updateLastMessage(String messageId) {
        this.lastMessageId = messageId;
        this.lastMessageTime = Instant.now().toString();
        this.updatedAt = Instant.now();
    }
}
