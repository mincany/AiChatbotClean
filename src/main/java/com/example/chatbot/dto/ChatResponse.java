package com.example.chatbot.dto;

import java.util.List;

public class ChatResponse {
    private String response;
    private List<SourceInfo> sources;
    private String sessionId;
    private Integer tokensUsed;
    private String knowledgeBaseId;
    private Integer contextChunksUsed;
    private Double minScore;
    private Double maxScore;

    public ChatResponse() {}

    public ChatResponse(String response, String knowledgeBaseId) {
        this.response = response;
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<SourceInfo> getSources() {
        return sources;
    }

    public void setSources(List<SourceInfo> sources) {
        this.sources = sources;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Integer getContextChunksUsed() {
        return contextChunksUsed;
    }

    public void setContextChunksUsed(Integer contextChunksUsed) {
        this.contextChunksUsed = contextChunksUsed;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }

    public static class SourceInfo {
        private String chunkId;
        private String documentName;
        private Integer chunkIndex;
        private Double relevanceScore;

        public SourceInfo() {}

        public SourceInfo(String chunkId, String documentName, Integer chunkIndex, Double relevanceScore) {
            this.chunkId = chunkId;
            this.documentName = documentName;
            this.chunkIndex = chunkIndex;
            this.relevanceScore = relevanceScore;
        }

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public String getDocumentName() {
            return documentName;
        }

        public void setDocumentName(String documentName) {
            this.documentName = documentName;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public Double getRelevanceScore() {
            return relevanceScore;
        }

        public void setRelevanceScore(Double relevanceScore) {
            this.relevanceScore = relevanceScore;
        }
    }
} 