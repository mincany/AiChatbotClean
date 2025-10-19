package com.example.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * SQS message payload for knowledge base processing
 */
public class KnowledgeProcessingMessage {

    @NotBlank(message = "Knowledge base ID is required")
    @JsonProperty("knowledge_base_id")
    private String knowledgeBaseId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "S3 key is required")
    @JsonProperty("s3_key")
    private String s3Key;

    @NotBlank(message = "Original filename is required")
    @JsonProperty("original_filename")
    private String originalFilename;

    @NotNull(message = "File size is required")
    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("retry_count")
    private Integer retryCount;

    // Default constructor for Jackson
    public KnowledgeProcessingMessage() {
        this.createdAt = Instant.now();
        this.retryCount = 0;
    }

    public KnowledgeProcessingMessage(String knowledgeBaseId, String userId, String s3Key, 
                                    String originalFilename, Long fileSize) {
        this();
        this.knowledgeBaseId = knowledgeBaseId;
        this.userId = userId;
        this.s3Key = s3Key;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
    }

    // Getters and setters
    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public String toString() {
        return "KnowledgeProcessingMessage{" +
                "knowledgeBaseId='" + knowledgeBaseId + '\'' +
                ", userId='" + userId + '\'' +
                ", s3Key='" + s3Key + '\'' +
                ", originalFilename='" + originalFilename + '\'' +
                ", fileSize=" + fileSize +
                ", createdAt=" + createdAt +
                ", retryCount=" + retryCount +
                '}';
    }
}
