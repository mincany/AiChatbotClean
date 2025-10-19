package com.example.chatbot.service;

import com.example.chatbot.dto.KnowledgeProcessingMessage;
import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

@Service
public class KnowledgeProcessingListener {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeProcessingListener.class);
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_DELAY_SECONDS = 60; // 1 minute

    private final S3Service s3Service;
    private final FileService fileService;
    private final OpenAiService openAiService;
    private final PineconeService pineconeService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SqsService sqsService;
    private final ObjectMapper objectMapper;

    @Autowired
    public KnowledgeProcessingListener(S3Service s3Service,
                                     FileService fileService,
                                     OpenAiService openAiService,
                                     PineconeService pineconeService,
                                     KnowledgeBaseRepository knowledgeBaseRepository,
                                     SqsService sqsService,
                                     ObjectMapper objectMapper) {
        this.s3Service = s3Service;
        this.fileService = fileService;
        this.openAiService = openAiService;
        this.pineconeService = pineconeService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.sqsService = sqsService;
        this.objectMapper = objectMapper;
    }

    /**
     * Listen for knowledge processing messages from SQS
     * 
     * @param messageBody The SQS message body
     */
    @SqsListener("${aws.sqs.knowledge-processing-queue}")
    public void processKnowledgeFile(@Payload String messageBody) {
        
        KnowledgeProcessingMessage message = null;
        Path tempFile = null;
        
        try {
            // Parse the message
            message = objectMapper.readValue(messageBody, KnowledgeProcessingMessage.class);
            
            logger.info("Received knowledge processing message: knowledgeBaseId={}, s3Key={}", 
                    message.getKnowledgeBaseId(), message.getS3Key());

            // Update status to processing
            updateKnowledgeBaseStatus(message.getKnowledgeBaseId(), "processing");

            // Download file from S3
            tempFile = s3Service.downloadFile(message.getS3Key());
            
            logger.info("Downloaded file from S3: {} -> {}", message.getS3Key(), tempFile);

            // Extract text from file
            String text = extractTextFromFile(tempFile, message.getOriginalFilename());
            
            logger.info("Extracted text from file: {} characters", text.length());

            // Store document chunks in Pinecone with user namespace
            pineconeService.upsertDocumentChunks(message.getUserId(), message.getKnowledgeBaseId(), text, openAiService);
            
            logger.info("Successfully stored document chunks in Pinecone for knowledge base: {}", 
                    message.getKnowledgeBaseId());

            // Update status to completed
            updateKnowledgeBaseStatus(message.getKnowledgeBaseId(), "completed");
            
            logger.info("Successfully completed processing for knowledge base: {}", message.getKnowledgeBaseId());

        } catch (Exception e) {
            logger.error("Error processing knowledge file: knowledgeBaseId={}, error={}", 
                    message != null ? message.getKnowledgeBaseId() : "unknown", e.getMessage(), e);
            
            if (message != null) {
                handleProcessingError(message, e);
            }
            
            // Re-throw exception to trigger SQS retry mechanism
            throw new RuntimeException("Knowledge processing failed", e);
            
        } finally {
            // Clean up temporary file
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                    logger.debug("Cleaned up temporary file: {}", tempFile);
                } catch (Exception e) {
                    logger.warn("Failed to clean up temporary file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Extract text from downloaded file
     */
    private String extractTextFromFile(Path filePath, String originalFilename) {
        try {
            // Read file content
            byte[] fileContent = java.nio.file.Files.readAllBytes(filePath);
            
            // Create a mock MultipartFile for FileService
            ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(fileContent, originalFilename);
            
            // Extract text using FileService
            return fileService.extractText(multipartFile);
            
        } catch (Exception e) {
            logger.error("Error extracting text from file: {}", originalFilename, e);
            throw new RuntimeException("Failed to extract text from file", e);
        }
    }

    /**
     * Handle processing errors with retry logic
     */
    private void handleProcessingError(KnowledgeProcessingMessage message, Exception error) {
        try {
            int currentRetryCount = message.getRetryCount() != null ? message.getRetryCount() : 0;
            
            if (currentRetryCount < MAX_RETRY_COUNT) {
                // Increment retry count and send back to queue with delay
                message.setRetryCount(currentRetryCount + 1);
                
                int delaySeconds = RETRY_DELAY_SECONDS * (int) Math.pow(2, currentRetryCount); // Exponential backoff
                
                logger.info("Scheduling retry for knowledge base: {} (attempt {}/{}), delay: {}s", 
                        message.getKnowledgeBaseId(), message.getRetryCount(), MAX_RETRY_COUNT, delaySeconds);
                
                sqsService.sendKnowledgeProcessingMessageWithDelay(message, delaySeconds);
                
                // Update status to indicate retry
                updateKnowledgeBaseStatus(message.getKnowledgeBaseId(), "retrying");
                
            } else {
                // Max retries exceeded, mark as failed
                logger.error("Max retries exceeded for knowledge base: {}, marking as failed", 
                        message.getKnowledgeBaseId());
                
                updateKnowledgeBaseStatus(message.getKnowledgeBaseId(), "failed");
            }
            
        } catch (Exception e) {
            logger.error("Error handling processing failure for knowledge base: {}", 
                    message.getKnowledgeBaseId(), e);
        }
    }

    /**
     * Update knowledge base status in database
     */
    private void updateKnowledgeBaseStatus(String knowledgeBaseId, String status) {
        try {
            Optional<KnowledgeBase> kbOptional = knowledgeBaseRepository.findById(knowledgeBaseId);
            if (kbOptional.isPresent()) {
                KnowledgeBase kb = kbOptional.get();
                kb.setStatus(status);
                kb.setUpdatedAt(Instant.now());
                knowledgeBaseRepository.save(kb);
                logger.debug("Updated knowledge base {} status to: {}", knowledgeBaseId, status);
            } else {
                logger.warn("Knowledge base not found for status update: {}", knowledgeBaseId);
            }
        } catch (Exception e) {
            logger.error("Error updating status for knowledge base: {}", knowledgeBaseId, e);
        }
    }

    /**
     * Inner class to simulate MultipartFile from byte array
     */
    private static class ByteArrayMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final byte[] content;
        private final String filename;

        public ByteArrayMultipartFile(byte[] content, String filename) {
            this.content = content;
            this.filename = filename;
        }

        @Override
        @NonNull
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return null; // Let Tika detect it
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        @NonNull
        public byte[] getBytes() {
            return content;
        }

        @Override
        @NonNull
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(@NonNull java.io.File dest) throws java.io.IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }

        @Override
        public void transferTo(@NonNull java.nio.file.Path dest) throws java.io.IOException, IllegalStateException {
            java.nio.file.Files.write(dest, content);
        }
    }
}
