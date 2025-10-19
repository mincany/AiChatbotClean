package com.example.chatbot.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;

@DynamoDbBean
public class KnowledgeBase {
    private String knowledgeId;
    private String userId;
    private String name;
    private String description;
    private String fileName;
    private String data; // S3 URL or link
    private Long fileSize;
    private String status;
    private String pineconeNamespace;
    private Instant createdAt;
    private Instant updatedAt;

    public KnowledgeBase() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public KnowledgeBase(String knowledgeId, String userId, String fileName, String status) {
        this();
        this.knowledgeId = knowledgeId;
        this.userId = userId;
        this.fileName = fileName;
        this.status = status;
        this.pineconeNamespace = knowledgeId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("KnowledgeId")
    public String getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(String knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    // Legacy getter for backward compatibility
    public String getId() {
        return knowledgeId;
    }

    public void setId(String id) {
        this.knowledgeId = id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"UserId-index"})
    @DynamoDbAttribute("UserId")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPineconeNamespace() {
        return pineconeNamespace;
    }

    public void setPineconeNamespace(String pineconeNamespace) {
        this.pineconeNamespace = pineconeNamespace;
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
} 