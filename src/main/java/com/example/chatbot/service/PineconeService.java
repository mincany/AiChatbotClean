package com.example.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Duration;
import java.util.*;

@Service
public class PineconeService {

    private static final Logger logger = LoggerFactory.getLogger(PineconeService.class);
    private static final int EMBEDDING_DIMENSION = 1536; // OpenAI text-embedding-ada-002 dimension
    private static final String METRIC = "cosine";
    private static final int MAX_CHUNK_SIZE = 1000; // characters per chunk
    private static final int CHUNK_OVERLAP = 200; // characters overlap between chunks

    private final WebClient pineconeWebClient;
    private final WebClient pineconeControlClient;
    private final String indexName;
    private final ObjectMapper objectMapper;

    @Autowired
    public PineconeService(WebClient pineconeWebClient, String pineconeIndexName,
                          @Value("${pinecone.api-key}") String apiKey) {
        this.pineconeWebClient = pineconeWebClient;
        this.indexName = pineconeIndexName;
        this.objectMapper = new ObjectMapper();
        
        // Create a separate client for control plane operations (index management)
        this.pineconeControlClient = WebClient.builder()
                .baseUrl("https://api.pinecone.io")
                .defaultHeader("Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        
        // Initialize index on startup
        initializeIndex();
    }
    
    /**
     * Initialize Pinecone index on startup
     */
    private void initializeIndex() {
        try {
            logger.info("Initializing Pinecone index: {}", indexName);
            
            // Check if index exists
            if (!indexExists()) {
                logger.info("Index {} does not exist. Creating it...", indexName);
                createIndex();
                
                // Wait for index to be ready
                waitForIndexReady();
            } else {
                logger.info("Index {} already exists and is ready", indexName);
            }
            
            // Test connection
            testConnection();
            
        } catch (Exception e) {
            logger.error("Failed to initialize Pinecone index: {}", e.getMessage(), e);
            throw new RuntimeException("Pinecone initialization failed", e);
        }
    }
    
    /**
     * Check if index exists
     */
    private boolean indexExists() {
        try {
            JsonNode response = pineconeControlClient.get()
                    .uri("/indexes/{indexName}", indexName)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return response != null && response.has("name");
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw new RuntimeException("Error checking index existence: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create a new Pinecone index
     */
    private void createIndex() {
        try {
            ObjectNode createRequest = objectMapper.createObjectNode();
            createRequest.put("name", indexName);
            createRequest.put("dimension", EMBEDDING_DIMENSION);
            createRequest.put("metric", METRIC);
            
            // Configure index spec for serverless
            ObjectNode spec = objectMapper.createObjectNode();
            ObjectNode serverless = objectMapper.createObjectNode();
            serverless.put("cloud", "aws");
            serverless.put("region", "us-east-1");
            spec.set("serverless", serverless);
            createRequest.set("spec", spec);
            
            pineconeControlClient.post()
                    .uri("/indexes")
                    .bodyValue(createRequest)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            logger.info("Successfully created Pinecone index: {}", indexName);
            
        } catch (Exception e) {
            logger.error("Failed to create Pinecone index: {}", e.getMessage(), e);
            throw new RuntimeException("Index creation failed", e);
        }
    }
    
    /**
     * Wait for index to be ready
     */
    private void waitForIndexReady() {
        logger.info("Waiting for index {} to be ready...", indexName);
        
        int maxAttempts = 60; // 5 minutes max wait
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            try {
                JsonNode response = pineconeControlClient.get()
                        .uri("/indexes/{indexName}", indexName)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();
                
                if (response != null && response.has("status")) {
                    JsonNode status = response.get("status");
                    if (status.has("ready") && status.get("ready").asBoolean()) {
                        logger.info("Index {} is ready", indexName);
                        return;
                    }
                }
                
                Thread.sleep(5000); // Wait 5 seconds
                attempts++;
                
            } catch (Exception e) {
                logger.warn("Error checking index status, attempt {}: {}", attempts + 1, e.getMessage());
                attempts++;
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for index", ie);
                }
            }
        }
        
        throw new RuntimeException("Index did not become ready within timeout period");
    }
    
    /**
     * Test Pinecone connection
     */
    public void testConnection() {
        try {
            logger.debug("Testing Pinecone connection...");
            
            // Use describe index stats to test connection
            JsonNode response = pineconeWebClient.post()
                    .uri("/describe_index_stats")
                    .bodyValue(objectMapper.createObjectNode())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null) {
                logger.info("Pinecone connection test successful. Index stats: {}", response.toString());
            } else {
                throw new RuntimeException("No response from Pinecone");
            }
            
        } catch (Exception e) {
            logger.error("Pinecone connection test failed: {}", e.getMessage(), e);
            throw new RuntimeException("Pinecone connection failed", e);
        }
    }
    
    /**
     * Split text into chunks for better embedding quality
     */
    public List<String> splitTextIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        
        text = text.trim();
        
        // If text is smaller than max chunk size, return as single chunk
        if (text.length() <= MAX_CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }
        
        int startPos = 0;
        
        while (startPos < text.length()) {
            int endPos = Math.min(startPos + MAX_CHUNK_SIZE, text.length());
            
            // Try to break at sentence boundary if possible
            if (endPos < text.length()) {
                int lastSentenceEnd = text.lastIndexOf('.', endPos);
                int lastNewlineEnd = text.lastIndexOf('\n', endPos);
                int lastSpaceEnd = text.lastIndexOf(' ', endPos);
                
                // Use the best break point available
                if (lastSentenceEnd > startPos + MAX_CHUNK_SIZE / 2) {
                    endPos = lastSentenceEnd + 1;
                } else if (lastNewlineEnd > startPos + MAX_CHUNK_SIZE / 2) {
                    endPos = lastNewlineEnd;
                } else if (lastSpaceEnd > startPos + MAX_CHUNK_SIZE / 2) {
                    endPos = lastSpaceEnd;
                }
            }
            
            String chunk = text.substring(startPos, endPos).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            // Move start position with overlap
            startPos = Math.max(endPos - CHUNK_OVERLAP, endPos);
        }
        
        logger.debug("Split text into {} chunks", chunks.size());
        return chunks;
    }
    
    /**
     * Upsert document chunks into Pinecone with user namespace
     */
    public void upsertDocumentChunks(String userId, String knowledgeBaseId, String documentText, 
                                   OpenAiService openAiService) {
        try {
            logger.info("Upserting document chunks for knowledge base: {} (user: {})", knowledgeBaseId, userId);
            
            // Split document into chunks
            List<String> chunks = splitTextIntoChunks(documentText);
            
            if (chunks.isEmpty()) {
                logger.warn("No chunks generated from document for knowledge base: {}", knowledgeBaseId);
                return;
            }
            
            // Process chunks in batches of 10 (Pinecone limit)
            int batchSize = 10;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
                upsertChunkBatch(userId, knowledgeBaseId, batch, i, openAiService);
            }
            
            logger.info("Successfully upserted {} chunks for knowledge base: {}", chunks.size(), knowledgeBaseId);
            
        } catch (Exception e) {
            logger.error("Failed to upsert document chunks for knowledge base: {}", knowledgeBaseId, e);
            throw new RuntimeException("Failed to upsert document chunks: " + e.getMessage(), e);
        }
    }
    
    /**
     * Upsert a batch of chunks
     */
    private void upsertChunkBatch(String userId, String knowledgeBaseId, List<String> chunks, 
                                int startIndex, OpenAiService openAiService) {
        try {
            // Generate embeddings for all chunks in batch
            List<List<Double>> embeddings = new ArrayList<>();
            for (String chunk : chunks) {
                List<Double> embedding = openAiService.createEmbedding(chunk);
                embeddings.add(embedding);
            }
            
            // Prepare vectors for upsert
            ArrayNode vectors = objectMapper.createArrayNode();
            
            for (int i = 0; i < chunks.size(); i++) {
                String vectorId = String.format("%s_chunk_%d", knowledgeBaseId, startIndex + i);
                
                // Prepare metadata
                ObjectNode metadata = objectMapper.createObjectNode();
                metadata.put("knowledge_base_id", knowledgeBaseId);
                metadata.put("user_id", userId);
                metadata.put("text_content", chunks.get(i));
                metadata.put("chunk_index", startIndex + i);
                metadata.put("total_chunks", chunks.size());
                metadata.put("created_at", System.currentTimeMillis());
                
                // Prepare vector object
                ObjectNode vector = objectMapper.createObjectNode();
                vector.put("id", vectorId);
                vector.set("values", objectMapper.valueToTree(embeddings.get(i)));
                vector.set("metadata", metadata);
                
                vectors.add(vector);
            }
            
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.set("vectors", vectors);
            requestBody.put("namespace", userId); // Use userId as namespace for isolation
            
            // Make API call with retry logic
            JsonNode result = pineconeWebClient.post()
                    .uri("/vectors/upsert")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .retry(3)
                    .block();
            
            if (result == null || !result.has("upsertedCount")) {
                throw new RuntimeException("Invalid response from Pinecone upsert API");
            }
            
            int upsertedCount = result.get("upsertedCount").asInt();
            logger.debug("Upserted {} vectors for knowledge base: {}", upsertedCount, knowledgeBaseId);
            
        } catch (Exception e) {
            logger.error("Failed to upsert chunk batch for knowledge base: {}", knowledgeBaseId, e);
            throw new RuntimeException("Failed to upsert chunk batch: " + e.getMessage(), e);
        }
    }

    /**
     * Query vectors in user's namespace with advanced filtering and top-k retrieval
     */
    public List<ContextChunk> queryVectors(String userId, String knowledgeBaseId, List<Double> queryEmbedding, 
                                         int topK, double scoreThreshold) {
        try {
            logger.debug("Querying vectors for user: {}, knowledge base: {}, topK: {}", userId, knowledgeBaseId, topK);
            logger.debug("🔍 Query embedding length: {}, Score threshold: {}", 
                    queryEmbedding.size(), scoreThreshold);
            
            // Prepare filter for specific knowledge base
            ObjectNode filter = objectMapper.createObjectNode();
            filter.put("knowledge_base_id", knowledgeBaseId);
            
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("namespace", userId); // Query user's namespace
            requestBody.put("topK", Math.max(1, Math.min(topK, 100))); // Limit between 1-100
            requestBody.put("includeMetadata", true);
            requestBody.put("includeValues", false); // We don't need the embedding values back
            requestBody.set("vector", objectMapper.valueToTree(queryEmbedding));
            requestBody.set("filter", filter);

            // Make API call
            JsonNode result = pineconeWebClient.post()
                    .uri("/query")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (result == null || !result.has("matches") || !result.get("matches").isArray()) {
                logger.warn("Invalid response from Pinecone query API for knowledge base: {}", knowledgeBaseId);
                return new ArrayList<>();
            }

            ArrayNode matches = (ArrayNode) result.get("matches");
            List<ContextChunk> contextChunks = new ArrayList<>();
            
            for (JsonNode match : matches) {
                double score = match.has("score") ? match.get("score").asDouble() : 0.0;
                
                // Filter by score threshold
                if (score < scoreThreshold) {
                    continue;
                }
                
                if (match.has("metadata")) {
                    JsonNode metadata = match.get("metadata");
                    
                    String textContent = metadata.has("text_content") ? 
                            metadata.get("text_content").asText() : "";
                    int chunkIndex = metadata.has("chunk_index") ? 
                            metadata.get("chunk_index").asInt() : 0;
                    String vectorId = match.has("id") ? match.get("id").asText() : "";
                    
                    if (!textContent.isEmpty()) {
                        contextChunks.add(new ContextChunk(vectorId, textContent, score, chunkIndex));
                    }
                }
            }
            
            // Sort by score descending
            contextChunks.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            logger.debug("Found {} relevant chunks for knowledge base: {} (after filtering)", 
                    contextChunks.size(), knowledgeBaseId);
            
            // Log detailed context information
            for (int i = 0; i < contextChunks.size(); i++) {
                ContextChunk chunk = contextChunks.get(i);
                logger.info("📄 Context Chunk {}: [Score: {:.4f}] [KB: {}] [Length: {}] Content: '{}'", 
                        i + 1, chunk.getScore(), knowledgeBaseId, chunk.getContent().length(), 
                        chunk.getContent().length() > 200 ? 
                            chunk.getContent().substring(0, 200) + "..." : chunk.getContent());
            }
            
            return contextChunks;

        } catch (Exception e) {
            logger.error("Failed to query vectors for knowledge base: {}", knowledgeBaseId, e);
            throw new RuntimeException("Failed to query vectors from Pinecone: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy method - updated to use new query method with user namespace
     */
    public String queryVector(String knowledgeBaseId, List<Double> queryEmbedding) {
        try {
            // For legacy compatibility, we need to extract userId from context
            // This is a limitation of the legacy API - we'll use knowledgeBaseId as namespace for now
            logger.warn("Using legacy queryVector method. Consider upgrading to queryVectors for better performance.");
            
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("namespace", knowledgeBaseId); // Legacy: use knowledgeBaseId as namespace
            requestBody.put("topK", 3);
            requestBody.put("includeMetadata", true);
            requestBody.set("vector", objectMapper.valueToTree(queryEmbedding));

            // Make API call
            JsonNode result = pineconeWebClient.post()
                    .uri("/query")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (result == null || !result.has("matches") || !result.get("matches").isArray()) {
                throw new RuntimeException("Invalid response from Pinecone query API");
            }

            ArrayNode matches = (ArrayNode) result.get("matches");
            if (matches.size() == 0) {
                return "No relevant content found in the knowledge base.";
            }

            // Extract and combine text from matches
            StringBuilder contextBuilder = new StringBuilder();
            for (JsonNode match : matches) {
                if (match.has("metadata") && match.get("metadata").has("text_content")) {
                    String textContent = match.get("metadata").get("text_content").asText();
                    if (!textContent.isEmpty()) {
                        contextBuilder.append(textContent).append("\n\n");
                    }
                }
            }

            String context = contextBuilder.toString().trim();
            return context.isEmpty() ? "No relevant content found in the knowledge base." : context;

        } catch (Exception e) {
            throw new RuntimeException("Failed to query vector from Pinecone: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy method - kept for backward compatibility but now delegates to new chunking method
     */
    public void upsertVector(String knowledgeBaseId, List<Double> embedding, String text) {
        logger.warn("Using legacy upsertVector method. Consider using upsertDocumentChunks for better performance.");
        
        try {
            // Create vector ID
            String vectorId = "chunk_" + UUID.randomUUID().toString().substring(0, 8);

            // Prepare metadata
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("knowledge_base_id", knowledgeBaseId);
            metadata.put("text_content", text);
            metadata.put("chunk_index", 0);

            // Prepare vector object
            ObjectNode vector = objectMapper.createObjectNode();
            vector.put("id", vectorId);
            vector.set("values", objectMapper.valueToTree(embedding));
            vector.set("metadata", metadata);

            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode vectors = objectMapper.createArrayNode();
            vectors.add(vector);
            requestBody.set("vectors", vectors);
            requestBody.put("namespace", knowledgeBaseId); // Legacy: use knowledgeBaseId as namespace

            // Make API call
            JsonNode result = pineconeWebClient.post()
                    .uri("/vectors/upsert")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (result == null || !result.has("upsertedCount")) {
                throw new RuntimeException("Invalid response from Pinecone upsert API");
            }

            int upsertedCount = result.get("upsertedCount").asInt();
            if (upsertedCount == 0) {
                throw new RuntimeException("No vectors were upserted to Pinecone");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert vector to Pinecone: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all vectors in a user's namespace for a specific knowledge base
     */
    public void deleteKnowledgeBase(String userId, String knowledgeBaseId) {
        try {
            logger.info("Deleting knowledge base vectors: {} for user: {}", knowledgeBaseId, userId);
            
            // Prepare filter to delete only vectors for this knowledge base
            ObjectNode filter = objectMapper.createObjectNode();
            filter.put("knowledge_base_id", knowledgeBaseId);
            
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("namespace", userId);
            requestBody.set("filter", filter);

            // Make API call
            pineconeWebClient.post()
                    .uri("/vectors/delete")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            logger.info("Successfully deleted vectors for knowledge base: {}", knowledgeBaseId);

        } catch (Exception e) {
            logger.error("Failed to delete knowledge base vectors: {}", knowledgeBaseId, e);
            throw new RuntimeException("Failed to delete knowledge base from Pinecone: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy method - kept for backward compatibility
     */
    public void deleteNamespace(String knowledgeBaseId) {
        logger.warn("Using legacy deleteNamespace method. Consider using deleteKnowledgeBase for better isolation.");
        
        try {
            // Prepare request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("deleteAll", true);
            requestBody.put("namespace", knowledgeBaseId);

            // Make API call
            pineconeWebClient.post()
                    .uri("/vectors/delete")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete namespace from Pinecone: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get index statistics
     */
    public JsonNode getIndexStats() {
        try {
            return pineconeWebClient.post()
                    .uri("/describe_index_stats")
                    .bodyValue(objectMapper.createObjectNode())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            logger.error("Failed to get index stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get index statistics", e);
        }
    }

    /**
     * Data class to represent a context chunk with score
     */
    public static class ContextChunk {
        private final String vectorId;
        private final String content;
        private final double score;
        private final int chunkIndex;

        public ContextChunk(String vectorId, String content, double score, int chunkIndex) {
            this.vectorId = vectorId;
            this.content = content;
            this.score = score;
            this.chunkIndex = chunkIndex;
        }

        public String getVectorId() { return vectorId; }
        public String getContent() { return content; }
        public double getScore() { return score; }
        public int getChunkIndex() { return chunkIndex; }

        @Override
        public String toString() {
            return String.format("ContextChunk{vectorId='%s', score=%.3f, chunkIndex=%d, content='%s...'}",
                    vectorId, score, chunkIndex, 
                    content.length() > 50 ? content.substring(0, 50) : content);
        }
    }
}