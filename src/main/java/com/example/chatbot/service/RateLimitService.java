package com.example.chatbot.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Check if request is allowed under rate limit
     * 
     * @param key Rate limit key (usually API key or IP)
     * @param limit Number of requests allowed
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if rate limited
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(limit, windowSeconds));
        return bucket.tryConsume(1);
    }

    /**
     * Get remaining tokens for a key
     */
    public long getRemainingTokens(String key, int limit, int windowSeconds) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(limit, windowSeconds));
        return bucket.getAvailableTokens();
    }

    /**
     * Create a new rate limiting bucket
     */
    private Bucket createBucket(int limit, int windowSeconds) {
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofSeconds(windowSeconds)));
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Clear rate limit for a specific key (useful for testing or admin operations)
     */
    public void clearRateLimit(String key) {
        buckets.remove(key);
    }

    /**
     * Clear all rate limits (useful for testing)
     */
    public void clearAllRateLimits() {
        buckets.clear();
    }
}
