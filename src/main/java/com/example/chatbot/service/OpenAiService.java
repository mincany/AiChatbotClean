package com.example.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class OpenAiService {

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiService(WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = new ObjectMapper();
    }

    public List<Double> createEmbedding(String text) {
        try {
            // Prepare request
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "text-embedding-ada-002");
            requestBody.put("input", text);

            // Make API call
            Mono<JsonNode> response = openAiWebClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class);

            JsonNode result = response.block();
            
            if (result == null || !result.has("data") || !result.get("data").isArray()) {
                throw new RuntimeException("Invalid response from OpenAI embeddings API");
            }

            // Extract embedding vector
            ArrayNode dataArray = (ArrayNode) result.get("data");
            if (dataArray.size() == 0) {
                throw new RuntimeException("No embedding data returned from OpenAI");
            }

            JsonNode embeddingNode = dataArray.get(0).get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Invalid embedding format from OpenAI");
            }

            // Convert to List<Double>
            return StreamSupport.stream(embeddingNode.spliterator(), false)
                    .map(JsonNode::asDouble)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to create embedding: " + e.getMessage(), e);
        }
    }

    public String generateChatResponse(String context, String question) {
        try {
            // Prepare messages
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);

            ArrayNode messages = objectMapper.createArrayNode();
            
            // System message
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a helpful assistant that answers questions based on the provided context. " +
                    "If the answer cannot be found in the context, say so clearly.");
            messages.add(systemMessage);

            // User message with context
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", "Context: " + context + "\n\nQuestion: " + question);
            messages.add(userMessage);

            requestBody.set("messages", messages);

            // Make API call
            Mono<JsonNode> response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class);

            JsonNode result = response.block();
            
            if (result == null || !result.has("choices") || !result.get("choices").isArray()) {
                throw new RuntimeException("Invalid response from OpenAI chat API");
            }

            ArrayNode choices = (ArrayNode) result.get("choices");
            if (choices.size() == 0) {
                throw new RuntimeException("No choices returned from OpenAI");
            }

            JsonNode messageNode = choices.get(0).get("message");
            if (messageNode == null || !messageNode.has("content")) {
                throw new RuntimeException("Invalid message format from OpenAI");
            }

            return messageNode.get("content").asText();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate chat response: " + e.getMessage(), e);
        }
    }
} 