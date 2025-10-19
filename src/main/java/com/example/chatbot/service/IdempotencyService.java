package com.example.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);

    // In-memory cache for idempotency - in production, use Redis or similar
    private final ConcurrentMap<String, IdempotencyRecord> idempotencyCache = new ConcurrentHashMap<>();

    /**
     * Check if request is duplicate and return cached response if exists
     */
    public ResponseEntity<?> checkIdempotency(String key) {
        IdempotencyRecord record = idempotencyCache.get(key);
        
        if (record != null) {
            if (record.isExpired()) {
                idempotencyCache.remove(key);
                logger.debug("Expired idempotency record removed for key: {}", key);
                return null;
            }
            
            logger.info("Duplicate request detected, returning cached response for key: {}", key);
            return record.getResponse();
        }
        
        return null;
    }

    /**
     * Store response for idempotency
     */
    public void storeResponse(String key, ResponseEntity<?> response, long ttlSeconds) {
        IdempotencyRecord record = new IdempotencyRecord(response, ttlSeconds);
        idempotencyCache.put(key, record);
        logger.debug("Stored idempotency record for key: {}", key);
    }

    /**
     * Generate idempotency key from request parameters
     */
    public String generateKey(String baseKey, String apiKey, String requestBody) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(baseKey).append(":");
        keyBuilder.append(apiKey != null ? apiKey : "anonymous").append(":");
        
        if (requestBody != null && !requestBody.isEmpty()) {
            keyBuilder.append(requestBody.hashCode());
        }
        
        return keyBuilder.toString();
    }

    /**
     * Clear expired entries (should be called periodically)
     */
    public void cleanupExpiredEntries() {
        int removedCount = 0;
        for (String key : idempotencyCache.keySet()) {
            IdempotencyRecord record = idempotencyCache.get(key);
            if (record != null && record.isExpired()) {
                idempotencyCache.remove(key);
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            logger.info("Cleaned up {} expired idempotency records", removedCount);
        }
    }

    /**
     * Clear all idempotency records (useful for testing)
     */
    public void clearAll() {
        idempotencyCache.clear();
        logger.info("Cleared all idempotency records");
    }

    /**
     * Inner class to store idempotency records
     */
    private static class IdempotencyRecord {
        private final ResponseEntity<?> response;
        private final Instant expiryTime;

        public IdempotencyRecord(ResponseEntity<?> response, long ttlSeconds) {
            this.response = response;
            this.expiryTime = Instant.now().plusSeconds(ttlSeconds);
        }

        public ResponseEntity<?> getResponse() {
            return response;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }
}
