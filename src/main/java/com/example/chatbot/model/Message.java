package com.example.chatbot.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;

@DynamoDbBean
public class Message {
    private String conversationId;
    private String createdTime;
    private String messageId;
    private String messageContent;
    private String senderType;
    private String status;
    private String metadata;

    public Message() {
        this.createdTime = Instant.now().toString();
        this.status = "open";
    }

    public Message(String conversationId, String messageId, String messageContent, String senderType) {
        this();
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.messageContent = messageContent;
        this.senderType = senderType;
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
    @DynamoDbAttribute("CreatedTime")
    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"MessageId-index"})
    @DynamoDbAttribute("MessageId")
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    // Enum constants for sender types
    public static class SenderType {
        public static final String TEAM = "team";
        public static final String AI = "ai";
        public static final String CUSTOMER = "customer";
    }

    // Enum constants for status
    public static class Status {
        public static final String OPEN = "open";
        public static final String SOLVED = "solved";
        public static final String PENDING = "pending";
    }
}
