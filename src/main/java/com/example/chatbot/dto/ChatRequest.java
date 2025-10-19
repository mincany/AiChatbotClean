package com.example.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {
    @NotBlank(message = "Knowledge base ID is required")
    private String knowledgeBaseId;
    
    @NotBlank(message = "Question is required")
    private String question;
    
    private String sessionId;
    private Integer maxTokens;
    private Double temperature;

    public ChatRequest() {}

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
} 