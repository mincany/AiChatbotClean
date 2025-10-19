package com.example.chatbot.service;

import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.repository.KnowledgeBaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.lang.NonNull;

@Service
public class AsyncKnowledgeProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncKnowledgeProcessingService.class);

    private final FileService fileService;
    private final OpenAiService openAiService;
    private final PineconeService pineconeService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    public AsyncKnowledgeProcessingService(
            FileService fileService,
            OpenAiService openAiService,
            PineconeService pineconeService,
            KnowledgeBaseRepository knowledgeBaseRepository) {
        this.fileService = fileService;
        this.openAiService = openAiService;
        this.pineconeService = pineconeService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * Process file asynchronously from file path
     */
    @Async("knowledgeProcessingExecutor")
    public CompletableFuture<Void> processFileAsync(String knowledgeBaseId, String filePath) {
        logger.info("Starting async processing for knowledge base: {} from file: {}", knowledgeBaseId, filePath);
        
        try {
            // Update status to processing
            updateKnowledgeBaseStatus(knowledgeBaseId, "processing");
            
            // Read file from path
            byte[] fileContent = readFileFromPath(filePath);
            String filename = Paths.get(filePath).getFileName().toString();
            
            // Extract text from file content
            String text = extractTextFromFile(fileContent, filename);
            
            // Get user ID from knowledge base
            String userId = getUserIdFromKnowledgeBase(knowledgeBaseId);
            
            // Store document chunks in Pinecone with user namespace
            pineconeService.upsertDocumentChunks(userId, knowledgeBaseId, text, openAiService);
            
            // Update status to completed
            updateKnowledgeBaseStatus(knowledgeBaseId, "completed");
            
            logger.info("Successfully completed async processing for knowledge base: {}", knowledgeBaseId);
            
        } catch (Exception e) {
            logger.error("Error processing knowledge base: {}", knowledgeBaseId, e);
            updateKnowledgeBaseStatus(knowledgeBaseId, "failed");
            throw new RuntimeException("Processing failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Read file from file system path
     */
    private byte[] readFileFromPath(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable: " + filePath);
        }
        
        return Files.readAllBytes(path);
    }

    /**
     * Extract text from file content using FileService
     */
    private String extractTextFromFile(byte[] fileContent, String filename) {
        try {
            // Create a mock MultipartFile-like approach by using FileService directly
            // In a real implementation, you might want to extend FileService to handle byte arrays
            return fileService.extractText(new ByteArrayMultipartFile(fileContent, filename));
        } catch (Exception e) {
            logger.error("Error extracting text from file: {}", filename, e);
            throw new RuntimeException("Failed to extract text from file", e);
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
     * Get processing status
     */
    public String getProcessingStatus(String knowledgeBaseId) {
        Optional<KnowledgeBase> kbOptional = knowledgeBaseRepository.findById(knowledgeBaseId);
        return kbOptional.map(KnowledgeBase::getStatus).orElse("not_found");
    }

    /**
     * Get user ID from knowledge base record
     */
    private String getUserIdFromKnowledgeBase(String knowledgeBaseId) {
        Optional<KnowledgeBase> kbOptional = knowledgeBaseRepository.findById(knowledgeBaseId);
        if (kbOptional.isEmpty()) {
            throw new RuntimeException("Knowledge base not found: " + knowledgeBaseId);
        }
        return kbOptional.get().getUserId();
    }

    /**
     * Estimate completion time based on file size and current load
     */
    public Instant estimateCompletionTime(long fileSizeBytes) {
        // Updated estimation: consider chunking overhead, 500KB per minute processing time
        long estimatedMinutes = Math.max(1, fileSizeBytes / (512 * 1024));
        return Instant.now().plusSeconds(estimatedMinutes * 60);
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
        public void transferTo(@NonNull java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }

        @Override
        public void transferTo(@NonNull Path dest) throws IOException, IllegalStateException {
            Files.write(dest, content);
        }
    }
}
