package com.example.chatbot.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.time.Instant;

@DynamoDbBean
public class User {
    private String userId;
    private String subscriptionType;
    private String apiKey;
    private String name;
    private String email;
    private Instant createdAt;
    private String passwordHash;

    public User() {
        this.createdAt = Instant.now();
        this.subscriptionType = "free"; // Default subscription type
    }

    public User(String userId, String apiKey, String email) {
        this();
        this.userId = userId;
        this.apiKey = apiKey;
        this.email = email;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("UserId")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // Legacy getter for backward compatibility
    public String getId() {
        return userId;
    }

    public void setId(String id) {
        this.userId = id;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"APIKey-index"})
    @DynamoDbAttribute("APIKey")
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
} 