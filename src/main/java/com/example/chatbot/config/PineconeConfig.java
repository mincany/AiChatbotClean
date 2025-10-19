package com.example.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PineconeConfig {

    @Value("${pinecone.api-key}")
    private String apiKey;

    @Value("${pinecone.api-url}")
    private String apiUrl;

    @Value("${pinecone.index-name}")
    private String indexName;

    @Bean
    public WebClient pineconeWebClient() {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public String pineconeIndexName() {
        return indexName;
    }
} 