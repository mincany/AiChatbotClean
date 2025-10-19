package com.example.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableAsync
public class ChatbotApplication {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("🚀 Chatbot API with Pinecone Integration Started Successfully!");
        logger.info("📊 Features enabled:");
        logger.info("   ✅ User namespace isolation");
        logger.info("   ✅ Smart document chunking");
        logger.info("   ✅ Top-K similarity search");
        logger.info("   ✅ Keyword-enhanced queries");
        logger.info("   ✅ Automatic index management");
        logger.info("🌐 API available at: http://localhost:9090");
        logger.info("🔍 Health check: http://localhost:9090/api/v1/health/detailed");
    }
} 