package com.example.chatbot.service;

import com.example.chatbot.service.PineconeService.ContextChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple LLM-based reranking service for improved retrieval accuracy
 * 
 * Uses OpenAI to score relevance between query and context chunks
 * Much simpler than complex hybrid approaches but leverages LLM understanding
 */
@Service
public class LLMRerankingService {

    private static final Logger logger = LoggerFactory.getLogger(LLMRerankingService.class);
    
    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public LLMRerankingService(WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Rerank context chunks using LLM-based relevance scoring
     * 
     * @param query The user's question
     * @param chunks List of context chunks to rerank
     * @param maxResults Maximum number of results to return
     * @return Reranked list of context chunks
     */
    public List<ContextChunk> rerankWithLLM(String query, List<ContextChunk> chunks, int maxResults) {
        if (chunks == null || chunks.isEmpty() || chunks.size() <= 1) {
            return chunks;
        }

        logger.info("🔄 LLM Reranking {} chunks for query: '{}'", chunks.size(), 
                query.length() > 100 ? query.substring(0, 100) + "..." : query);

        try {
            // Score each chunk using LLM
            List<ScoredChunk> scoredChunks = new ArrayList<>();
            
            for (int i = 0; i < chunks.size(); i++) {
                ContextChunk chunk = chunks.get(i);
                double llmScore = scoreLLMRelevance(query, chunk.getContent(), i + 1, chunks.size());
                
                // Combine LLM score with original Pinecone score (weighted)
                double combinedScore = (llmScore * 0.7) + (chunk.getScore() * 0.3);
                
                scoredChunks.add(new ScoredChunk(chunk, combinedScore, llmScore));
            }
            
            // Sort by combined score and limit results
            List<ContextChunk> rerankedChunks = scoredChunks.stream()
                    .sorted((a, b) -> Double.compare(b.combinedScore, a.combinedScore))
                    .limit(maxResults)
                    .map(sc -> sc.chunk)
                    .collect(Collectors.toList());
            
            logRerankingResults(query, chunks, rerankedChunks, scoredChunks);
            
            return rerankedChunks;

        } catch (Exception e) {
            logger.warn("LLM reranking failed, falling back to original order: {}", e.getMessage());
            return chunks.stream().limit(maxResults).collect(Collectors.toList());
        }
    }

    /**
     * Score relevance between query and context using LLM
     */
    private double scoreLLMRelevance(String query, String context, int chunkNum, int totalChunks) {
        try {
            // Create a focused prompt for relevance scoring
            String prompt = buildScoringPrompt(query, context);
            
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("max_tokens", 10); // Very short response needed
            requestBody.put("temperature", 0.1); // Low temperature for consistent scoring
            
            ArrayNode messages = objectMapper.createArrayNode();
            
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a relevance scorer. Rate how well the context answers the query on a scale of 0-10. Respond with only a number.");
            messages.add(systemMessage);
            
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            
            requestBody.set("messages", messages);

            // Make API call
            Mono<JsonNode> response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class);

            JsonNode result = response.block();
            
            if (result != null && result.has("choices") && result.get("choices").isArray()) {
                ArrayNode choices = (ArrayNode) result.get("choices");
                if (choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null && messageNode.has("content")) {
                        String scoreText = messageNode.get("content").asText().trim();
                        
                        // Extract numeric score
                        double score = parseScore(scoreText);
                        
                        logger.debug("LLM scored chunk {}/{}: {:.1f} - '{}'", 
                                chunkNum, totalChunks, score, 
                                context.length() > 50 ? context.substring(0, 50) + "..." : context);
                        
                        return score / 10.0; // Normalize to 0-1 range
                    }
                }
            }
            
            // Fallback to original Pinecone score if LLM scoring fails
            logger.debug("LLM scoring failed for chunk {}/{}, using fallback", chunkNum, totalChunks);
            return 0.5; // Neutral score
            
        } catch (Exception e) {
            logger.debug("Error scoring chunk {}/{}: {}", chunkNum, totalChunks, e.getMessage());
            return 0.5; // Neutral score on error
        }
    }

    /**
     * Build a concise prompt for relevance scoring
     */
    private String buildScoringPrompt(String query, String context) {
        // Truncate context if too long to avoid token limits
        String truncatedContext = context.length() > 500 ? 
                context.substring(0, 500) + "..." : context;
        
        return String.format(
                "Query: %s\n\nContext: %s\n\nHow relevant is this context to answering the query? Score 0-10:",
                query, truncatedContext
        );
    }

    /**
     * Parse score from LLM response
     */
    private double parseScore(String scoreText) {
        try {
            // Try to extract first number found
            String numericPart = scoreText.replaceAll("[^0-9.]", "");
            if (!numericPart.isEmpty()) {
                double score = Double.parseDouble(numericPart);
                // Clamp to 0-10 range
                return Math.max(0, Math.min(10, score));
            }
        } catch (NumberFormatException e) {
            logger.debug("Could not parse score from: '{}'", scoreText);
        }
        
        // Fallback scoring based on keywords
        return scoreText.toLowerCase().contains("relevant") || 
               scoreText.toLowerCase().contains("good") || 
               scoreText.toLowerCase().contains("yes") ? 7.0 : 3.0;
    }

    /**
     * Log reranking results for analysis
     */
    private void logRerankingResults(String query, List<ContextChunk> original, 
                                   List<ContextChunk> reranked, List<ScoredChunk> scored) {
        logger.info("🎯 LLM Reranking Results for query: '{}'", 
                query.length() > 80 ? query.substring(0, 80) + "..." : query);
        
        logger.info("📊 Top {} results (Original → Reranked):", reranked.size());
        
        for (int i = 0; i < Math.min(3, reranked.size()); i++) {
            ContextChunk chunk = reranked.get(i);
            int originalPos = original.indexOf(chunk) + 1;
            
            // Find the scored chunk to get LLM score
            Optional<ScoredChunk> scoredChunk = scored.stream()
                    .filter(sc -> sc.chunk.equals(chunk))
                    .findFirst();
            
            double llmScore = scoredChunk.map(sc -> sc.llmScore).orElse(0.0);
            
            logger.info("  {}. [Orig: #{}] [Pinecone: {:.3f}] [LLM: {:.3f}] [Combined: {:.3f}] '{}'",
                    i + 1, originalPos, chunk.getScore(), llmScore, 
                    scoredChunk.map(sc -> sc.combinedScore).orElse(0.0),
                    chunk.getContent().length() > 80 ? 
                        chunk.getContent().substring(0, 80) + "..." : chunk.getContent());
        }
    }

    /**
     * Helper class for scoring chunks
     */
    private static class ScoredChunk {
        final ContextChunk chunk;
        final double combinedScore;
        final double llmScore;

        ScoredChunk(ContextChunk chunk, double combinedScore, double llmScore) {
            this.chunk = chunk;
            this.combinedScore = combinedScore;
            this.llmScore = llmScore;
        }
    }
}
