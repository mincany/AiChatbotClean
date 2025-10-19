package com.example.chatbot.dto;

public class RegisterResponse {
    private String userId;
    private String apiKey;
    private String email;
    private String message;

    public RegisterResponse() {}

    public RegisterResponse(String userId, String apiKey, String email, String message) {
        this.userId = userId;
        this.apiKey = apiKey;
        this.email = email;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 