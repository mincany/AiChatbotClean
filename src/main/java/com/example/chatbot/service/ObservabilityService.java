package com.example.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability Service for comprehensive logging and metrics
 * 
 * Provides structured logging ready for CloudWatch integration:
 * - Request/response tracking
 * - Context usage logging
 * - Performance metrics
 * - Error tracking
 * - Usage analytics
 */
@Service
public class ObservabilityService {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityService.class);
    private static final Logger metricsLogger = LoggerFactory.getLogger("METRICS");
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // In-memory counters (in production, use proper metrics system)
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);

    /**
     * Log chat query request with full context
     */
    public void logChatQuery(ChatQueryEvent event) {
        // Set MDC for structured logging
        MDC.put("userId", event.getUserId());
        MDC.put("sessionId", event.getSessionId());
        MDC.put("knowledgeBaseId", event.getKnowledgeBaseId());
        MDC.put("requestId", event.getRequestId());
        
        try {
            // Increment counters
            totalRequests.incrementAndGet();
            
            // Create structured log entry
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("eventType", "CHAT_QUERY");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", event.getUserId());
            logEntry.put("sessionId", event.getSessionId());
            logEntry.put("knowledgeBaseId", event.getKnowledgeBaseId());
            logEntry.put("requestId", event.getRequestId());
            logEntry.put("questionLength", event.getQuestion().length());
            logEntry.put("topK", event.getTopK());
            logEntry.put("scoreThreshold", event.getScoreThreshold());
            logEntry.put("rerankingEnabled", event.isRerankingEnabled());
            logEntry.put("rerankingStrategy", event.getRerankingStrategy());
            
            // Log query (truncated for privacy)
            String truncatedQuery = event.getQuestion().length() > 200 ? 
                    event.getQuestion().substring(0, 200) + "..." : event.getQuestion();
            logEntry.put("queryPreview", truncatedQuery);
            
            logger.info("📝 CHAT_QUERY: {}", objectMapper.writeValueAsString(logEntry));
            
            // Metrics logging for CloudWatch
            metricsLogger.info("METRIC_CHAT_QUERY userId={} knowledgeBaseId={} questionLength={} topK={} rerankingEnabled={}", 
                    event.getUserId(), event.getKnowledgeBaseId(), event.getQuestion().length(), 
                    event.getTopK(), event.isRerankingEnabled());
                    
        } catch (Exception e) {
            logger.error("Failed to log chat query event", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Log context retrieval and usage
     */
    public void logContextUsage(ContextUsageEvent event) {
        MDC.put("userId", event.getUserId());
        MDC.put("requestId", event.getRequestId());
        
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("eventType", "CONTEXT_USAGE");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", event.getUserId());
            logEntry.put("requestId", event.getRequestId());
            logEntry.put("knowledgeBaseId", event.getKnowledgeBaseId());
            logEntry.put("chunksRetrieved", event.getChunksRetrieved());
            logEntry.put("chunksUsed", event.getChunksUsed());
            logEntry.put("totalContextLength", event.getTotalContextLength());
            logEntry.put("minScore", event.getMinScore());
            logEntry.put("maxScore", event.getMaxScore());
            logEntry.put("avgScore", event.getAvgScore());
            logEntry.put("rerankingApplied", event.isRerankingApplied());
            
            // Log context chunks (sanitized)
            List<Map<String, Object>> chunks = new ArrayList<>();
            for (ContextChunkInfo chunk : event.getChunks()) {
                Map<String, Object> chunkInfo = new HashMap<>();
                chunkInfo.put("chunkId", chunk.getChunkId());
                chunkInfo.put("score", chunk.getScore());
                chunkInfo.put("length", chunk.getLength());
                chunkInfo.put("contentPreview", chunk.getContentPreview());
                chunks.add(chunkInfo);
            }
            logEntry.put("chunks", chunks);
            
            logger.info("📄 CONTEXT_USAGE: {}", objectMapper.writeValueAsString(logEntry));
            
            // Metrics for CloudWatch
            metricsLogger.info("METRIC_CONTEXT_USAGE userId={} chunksRetrieved={} chunksUsed={} totalLength={} avgScore={:.3f}", 
                    event.getUserId(), event.getChunksRetrieved(), event.getChunksUsed(), 
                    event.getTotalContextLength(), event.getAvgScore());
                    
        } catch (Exception e) {
            logger.error("Failed to log context usage event", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Log AI response generation
     */
    public void logAIResponse(AIResponseEvent event) {
        MDC.put("userId", event.getUserId());
        MDC.put("requestId", event.getRequestId());
        
        try {
            totalTokensUsed.addAndGet(event.getTokensUsed());
            
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("eventType", "AI_RESPONSE");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", event.getUserId());
            logEntry.put("requestId", event.getRequestId());
            logEntry.put("model", event.getModel());
            logEntry.put("tokensUsed", event.getTokensUsed());
            logEntry.put("promptTokens", event.getPromptTokens());
            logEntry.put("completionTokens", event.getCompletionTokens());
            logEntry.put("responseLength", event.getResponseLength());
            logEntry.put("processingTimeMs", event.getProcessingTimeMs());
            logEntry.put("cost", event.getCost());
            
            // Response preview (truncated)
            String responsePreview = event.getResponse().length() > 300 ? 
                    event.getResponse().substring(0, 300) + "..." : event.getResponse();
            logEntry.put("responsePreview", responsePreview);
            
            logger.info("🤖 AI_RESPONSE: {}", objectMapper.writeValueAsString(logEntry));
            
            // Metrics for CloudWatch
            metricsLogger.info("METRIC_AI_RESPONSE userId={} model={} tokensUsed={} cost={} processingTimeMs={}", 
                    event.getUserId(), event.getModel(), event.getTokensUsed(), 
                    event.getCost(), event.getProcessingTimeMs());
                    
        } catch (Exception e) {
            logger.error("Failed to log AI response event", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Log errors with full context
     */
    public void logError(ErrorEvent event) {
        MDC.put("userId", event.getUserId());
        MDC.put("requestId", event.getRequestId());
        
        try {
            totalErrors.incrementAndGet();
            
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("eventType", "ERROR");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", event.getUserId());
            logEntry.put("requestId", event.getRequestId());
            logEntry.put("errorType", event.getErrorType());
            logEntry.put("errorCode", event.getErrorCode());
            logEntry.put("errorMessage", event.getErrorMessage());
            logEntry.put("stackTrace", event.getStackTrace());
            logEntry.put("context", event.getContext());
            
            logger.error("❌ ERROR: {}", objectMapper.writeValueAsString(logEntry));
            
            // Metrics for CloudWatch
            metricsLogger.error("METRIC_ERROR userId={} errorType={} errorCode={}", 
                    event.getUserId(), event.getErrorType(), event.getErrorCode());
                    
        } catch (Exception e) {
            logger.error("Failed to log error event", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Log performance metrics
     */
    public void logPerformanceMetrics(PerformanceEvent event) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("eventType", "PERFORMANCE");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("operation", event.getOperation());
            logEntry.put("durationMs", event.getDurationMs());
            logEntry.put("success", event.isSuccess());
            logEntry.put("metadata", event.getMetadata());
            
            logger.info("⚡ PERFORMANCE: {}", objectMapper.writeValueAsString(logEntry));
            
            // Metrics for CloudWatch
            metricsLogger.info("METRIC_PERFORMANCE operation={} durationMs={} success={}", 
                    event.getOperation(), event.getDurationMs(), event.isSuccess());
                    
        } catch (Exception e) {
            logger.error("Failed to log performance event", e);
        }
    }

    /**
     * Log audit events for compliance
     */
    public void logAuditEvent(AuditEvent event) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("eventType", "AUDIT");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", event.getUserId());
            logEntry.put("action", event.getAction());
            logEntry.put("resource", event.getResource());
            logEntry.put("outcome", event.getOutcome());
            logEntry.put("details", event.getDetails());
            
            auditLogger.info("🔍 AUDIT: {}", objectMapper.writeValueAsString(logEntry));
            
        } catch (Exception e) {
            logger.error("Failed to log audit event", e);
        }
    }

    /**
     * Get current system metrics
     */
    public SystemMetrics getSystemMetrics() {
        return new SystemMetrics(
                totalRequests.get(),
                totalErrors.get(),
                totalTokensUsed.get(),
                totalErrors.get() > 0 ? (double) totalErrors.get() / totalRequests.get() : 0.0
        );
    }

    // Event classes for structured logging

    public static class ChatQueryEvent {
        private String userId;
        private String sessionId;
        private String knowledgeBaseId;
        private String requestId;
        private String question;
        private int topK;
        private double scoreThreshold;
        private boolean rerankingEnabled;
        private String rerankingStrategy;

        // Constructor and getters
        public ChatQueryEvent(String userId, String sessionId, String knowledgeBaseId, String requestId,
                            String question, int topK, double scoreThreshold, boolean rerankingEnabled, String rerankingStrategy) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.knowledgeBaseId = knowledgeBaseId;
            this.requestId = requestId;
            this.question = question;
            this.topK = topK;
            this.scoreThreshold = scoreThreshold;
            this.rerankingEnabled = rerankingEnabled;
            this.rerankingStrategy = rerankingStrategy;
        }

        // Getters
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public String getKnowledgeBaseId() { return knowledgeBaseId; }
        public String getRequestId() { return requestId; }
        public String getQuestion() { return question; }
        public int getTopK() { return topK; }
        public double getScoreThreshold() { return scoreThreshold; }
        public boolean isRerankingEnabled() { return rerankingEnabled; }
        public String getRerankingStrategy() { return rerankingStrategy; }
    }

    public static class ContextUsageEvent {
        private String userId;
        private String requestId;
        private String knowledgeBaseId;
        private int chunksRetrieved;
        private int chunksUsed;
        private int totalContextLength;
        private double minScore;
        private double maxScore;
        private double avgScore;
        private boolean rerankingApplied;
        private List<ContextChunkInfo> chunks;

        public ContextUsageEvent(String userId, String requestId, String knowledgeBaseId,
                               int chunksRetrieved, int chunksUsed, int totalContextLength,
                               double minScore, double maxScore, double avgScore, boolean rerankingApplied,
                               List<ContextChunkInfo> chunks) {
            this.userId = userId;
            this.requestId = requestId;
            this.knowledgeBaseId = knowledgeBaseId;
            this.chunksRetrieved = chunksRetrieved;
            this.chunksUsed = chunksUsed;
            this.totalContextLength = totalContextLength;
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.avgScore = avgScore;
            this.rerankingApplied = rerankingApplied;
            this.chunks = chunks;
        }

        // Getters
        public String getUserId() { return userId; }
        public String getRequestId() { return requestId; }
        public String getKnowledgeBaseId() { return knowledgeBaseId; }
        public int getChunksRetrieved() { return chunksRetrieved; }
        public int getChunksUsed() { return chunksUsed; }
        public int getTotalContextLength() { return totalContextLength; }
        public double getMinScore() { return minScore; }
        public double getMaxScore() { return maxScore; }
        public double getAvgScore() { return avgScore; }
        public boolean isRerankingApplied() { return rerankingApplied; }
        public List<ContextChunkInfo> getChunks() { return chunks; }
    }

    public static class ContextChunkInfo {
        private String chunkId;
        private double score;
        private int length;
        private String contentPreview;

        public ContextChunkInfo(String chunkId, double score, int length, String contentPreview) {
            this.chunkId = chunkId;
            this.score = score;
            this.length = length;
            this.contentPreview = contentPreview;
        }

        // Getters
        public String getChunkId() { return chunkId; }
        public double getScore() { return score; }
        public int getLength() { return length; }
        public String getContentPreview() { return contentPreview; }
    }

    public static class AIResponseEvent {
        private String userId;
        private String requestId;
        private String model;
        private int tokensUsed;
        private int promptTokens;
        private int completionTokens;
        private int responseLength;
        private long processingTimeMs;
        private double cost;
        private String response;

        public AIResponseEvent(String userId, String requestId, String model, int tokensUsed,
                             int promptTokens, int completionTokens, int responseLength,
                             long processingTimeMs, double cost, String response) {
            this.userId = userId;
            this.requestId = requestId;
            this.model = model;
            this.tokensUsed = tokensUsed;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.responseLength = responseLength;
            this.processingTimeMs = processingTimeMs;
            this.cost = cost;
            this.response = response;
        }

        // Getters
        public String getUserId() { return userId; }
        public String getRequestId() { return requestId; }
        public String getModel() { return model; }
        public int getTokensUsed() { return tokensUsed; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public int getResponseLength() { return responseLength; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public double getCost() { return cost; }
        public String getResponse() { return response; }
    }

    public static class ErrorEvent {
        private String userId;
        private String requestId;
        private String errorType;
        private String errorCode;
        private String errorMessage;
        private String stackTrace;
        private Map<String, Object> context;

        public ErrorEvent(String userId, String requestId, String errorType, String errorCode,
                         String errorMessage, String stackTrace, Map<String, Object> context) {
            this.userId = userId;
            this.requestId = requestId;
            this.errorType = errorType;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
            this.context = context;
        }

        // Getters
        public String getUserId() { return userId; }
        public String getRequestId() { return requestId; }
        public String getErrorType() { return errorType; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public String getStackTrace() { return stackTrace; }
        public Map<String, Object> getContext() { return context; }
    }

    public static class PerformanceEvent {
        private String operation;
        private long durationMs;
        private boolean success;
        private Map<String, Object> metadata;

        public PerformanceEvent(String operation, long durationMs, boolean success, Map<String, Object> metadata) {
            this.operation = operation;
            this.durationMs = durationMs;
            this.success = success;
            this.metadata = metadata;
        }

        // Getters
        public String getOperation() { return operation; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    public static class AuditEvent {
        private String userId;
        private String action;
        private String resource;
        private String outcome;
        private Map<String, Object> details;

        public AuditEvent(String userId, String action, String resource, String outcome, Map<String, Object> details) {
            this.userId = userId;
            this.action = action;
            this.resource = resource;
            this.outcome = outcome;
            this.details = details;
        }

        // Getters
        public String getUserId() { return userId; }
        public String getAction() { return action; }
        public String getResource() { return resource; }
        public String getOutcome() { return outcome; }
        public Map<String, Object> getDetails() { return details; }
    }

    public static class SystemMetrics {
        private long totalRequests;
        private long totalErrors;
        private long totalTokensUsed;
        private double errorRate;

        public SystemMetrics(long totalRequests, long totalErrors, long totalTokensUsed, double errorRate) {
            this.totalRequests = totalRequests;
            this.totalErrors = totalErrors;
            this.totalTokensUsed = totalTokensUsed;
            this.errorRate = errorRate;
        }

        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getTotalErrors() { return totalErrors; }
        public long getTotalTokensUsed() { return totalTokensUsed; }
        public double getErrorRate() { return errorRate; }
    }
}
