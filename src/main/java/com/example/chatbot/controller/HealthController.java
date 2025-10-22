package com.example.chatbot.controller;

import com.example.chatbot.service.PineconeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    private final PineconeService pineconeService;

    @Autowired
    public HealthController(PineconeService pineconeService) {
        this.pineconeService = pineconeService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "DOWN");
        response.put("service", "chatbot-api");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "Service is healthy and running with DevTools enabled");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Detailed health check including Pinecone status
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> services = new HashMap<>();
        
        // Check Pinecone connectivity
        Map<String, Object> pineconeStatus = new HashMap<>();
        try {
            pineconeService.testConnection();
            JsonNode stats = pineconeService.getIndexStats();
            
            pineconeStatus.put("status", "UP");
            pineconeStatus.put("connection", "SUCCESS");
            if (stats != null) {
                pineconeStatus.put("totalVectorCount", stats.has("totalVectorCount") ? 
                        stats.get("totalVectorCount").asLong() : 0);
                pineconeStatus.put("dimension", stats.has("dimension") ? 
                        stats.get("dimension").asInt() : 1536);
            }
        } catch (Exception e) {
            pineconeStatus.put("status", "DOWN");
            pineconeStatus.put("connection", "FAILED");
            pineconeStatus.put("error", e.getMessage());
            logger.error("Pinecone health check failed: {}", e.getMessage());
        }
        
        services.put("pinecone", pineconeStatus);
        
        // Overall status
        boolean allHealthy = services.values().stream()
                .allMatch(service -> "UP".equals(((Map<?, ?>) service).get("status")));
        
        response.put("status", allHealthy ? "UP" : "DEGRADED");
        response.put("service", "chatbot-api");
        response.put("timestamp", LocalDateTime.now());
        response.put("services", services);
        
        return ResponseEntity.ok(response);
    }
}
