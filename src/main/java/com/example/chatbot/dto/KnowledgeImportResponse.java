package com.example.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class KnowledgeImportResponse {
    
    @JsonProperty("knowledge_id")
    private String knowledgeId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("estimated_completion")
    private Instant estimatedCompletion;

    // Default constructor
    public KnowledgeImportResponse() {}

    // Constructor
    public KnowledgeImportResponse(String knowledgeId, String status, String message, Instant estimatedCompletion) {
        this.knowledgeId = knowledgeId;
        this.status = status;
        this.message = message;
        this.estimatedCompletion = estimatedCompletion;
    }

    // Static factory methods for common responses
    public static KnowledgeImportResponse processing(String knowledgeId, int estimatedMinutes) {
        Instant completion = Instant.now().plusSeconds(estimatedMinutes * 60L);
        return new KnowledgeImportResponse(
            knowledgeId,
            "processing",
            "File uploaded successfully. Processing will complete shortly.",
            completion
        );
    }

    public static KnowledgeImportResponse completed(String knowledgeId) {
        return new KnowledgeImportResponse(
            knowledgeId,
            "completed",
            "File processing completed successfully.",
            Instant.now()
        );
    }

    public static KnowledgeImportResponse failed(String knowledgeId, String reason) {
        return new KnowledgeImportResponse(
            knowledgeId,
            "failed",
            "File processing failed: " + reason,
            null
        );
    }

    // Getters and Setters
    public String getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(String knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getEstimatedCompletion() {
        return estimatedCompletion;
    }

    public void setEstimatedCompletion(Instant estimatedCompletion) {
        this.estimatedCompletion = estimatedCompletion;
    }
}
