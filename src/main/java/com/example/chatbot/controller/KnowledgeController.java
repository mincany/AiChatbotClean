package com.example.chatbot.controller;

import com.example.chatbot.annotation.Idempotent;
import com.example.chatbot.annotation.RateLimit;
import com.example.chatbot.dto.ApiResponse;
import com.example.chatbot.dto.KnowledgeImportRequest;
import com.example.chatbot.dto.KnowledgeImportResponse;
import com.example.chatbot.exception.BusinessException;
import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.repository.KnowledgeBaseRepository;
import com.example.chatbot.service.AsyncKnowledgeProcessingService;
import com.example.chatbot.service.UserService;
import com.example.chatbot.service.S3Service;
import com.example.chatbot.service.SqsService;
import com.example.chatbot.dto.KnowledgeProcessingMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@Validated
public class KnowledgeController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeController.class);

    private final AsyncKnowledgeProcessingService asyncProcessingService;
    private final UserService userService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final S3Service s3Service;
    private final SqsService sqsService;

    @Autowired
    public KnowledgeController(AsyncKnowledgeProcessingService asyncProcessingService,
                              UserService userService,
                              KnowledgeBaseRepository knowledgeBaseRepository,
                              S3Service s3Service,
                              SqsService sqsService) {
        this.asyncProcessingService = asyncProcessingService;
        this.userService = userService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.s3Service = s3Service;
        this.sqsService = sqsService;
    }

    /**
     * Import and process a knowledge base file via multipart upload
     * 
     * @param file The uploaded file
     * @param name The name for the knowledge base
     * @param description Optional description for the knowledge base
     * @param apiKey The user's API key for authentication
     * @param idempotencyKey Optional idempotency key for safe retries
     * @return ResponseEntity with processing status and estimated completion time
     */
    @PostMapping(value = "/import-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @RateLimit(key = "knowledge-import", limit = 10, window = 60, useApiKey = true)
    @Idempotent(keyParam = "idempotency_key", ttlSeconds = 3600, includeBody = true)
    public ResponseEntity<ApiResponse<KnowledgeImportResponse>> importFileMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") @NotBlank(message = "Name is required") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestHeader("X-API-Key") @NotBlank(message = "API key is required") String apiKey,
            @RequestParam(value = "idempotency_key", required = false) String idempotencyKey) throws IOException {
        
        logger.info("Processing multipart knowledge import request: file={}, name={}", 
                file.getOriginalFilename(), name);
        
        // Validate API key and get user ID
        String userId = userService.getUserIdFromApiKey(apiKey);
        if (userId == null) {
            logger.warn("Invalid API key provided: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
            throw new BusinessException("Invalid API key", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        // Validate file
        if (file.isEmpty()) {
            throw new BusinessException("File is required and cannot be empty", "BAD_REQUEST", HttpStatus.BAD_REQUEST);
        }
        
        try {
            // Generate knowledge base ID
            String knowledgeBaseId = "kb_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create knowledge base record with initial status
            String filename = file.getOriginalFilename();
            KnowledgeBase kb = new KnowledgeBase(knowledgeBaseId, userId, filename, "uploading");
            kb.setName(name);
            if (description != null && !description.trim().isEmpty()) {
                kb.setDescription(description.trim());
            }
            kb.setFileSize(file.getSize());
            
            knowledgeBaseRepository.save(kb);
            
            // Upload file to S3
            String s3Key = s3Service.generateS3Key(userId, knowledgeBaseId, filename);
            String s3Url = s3Service.uploadFile(file, s3Key, userId, knowledgeBaseId);
            
            // Update knowledge base with S3 URL and status
            kb.setData(s3Url);
            kb.setStatus("processing");
            knowledgeBaseRepository.save(kb);
            
            // Send message to SQS for async processing
            KnowledgeProcessingMessage message = new KnowledgeProcessingMessage(
                knowledgeBaseId, userId, s3Key, filename, file.getSize()
            );
            sqsService.sendKnowledgeProcessingMessage(message);
            
            // Estimate completion time based on file size
            Instant estimatedCompletion = asyncProcessingService.estimateCompletionTime(file.getSize());
            
            // Build response
            KnowledgeImportResponse response = new KnowledgeImportResponse(
                knowledgeBaseId, 
                "processing", 
                "File uploaded successfully and queued for processing",
                estimatedCompletion
            );
            
            logger.info("Knowledge import initiated successfully: knowledgeBaseId={}, estimatedCompletion={}", 
                    knowledgeBaseId, estimatedCompletion);
            
            return ResponseEntity.accepted()
                    .body(ApiResponse.success(response));
                    
        } catch (Exception e) {
            logger.error("Failed to process knowledge import: {}", e.getMessage(), e);
            throw new BusinessException("Failed to process file import: " + e.getMessage(), 
                    "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Import and process a knowledge base file via JSON request (for file paths)
     * 
     * This endpoint demonstrates all API best practices:
     * - DTOs for request/response (never expose entities)
     * - Input validation with @Valid and Bean Validation
     * - Proper HTTP status codes (202 Accepted for async operations)
     * - Rate limiting to prevent abuse
     * - Idempotency for safe retries
     * - Async processing for long-running operations
     * - Comprehensive logging
     * - Global exception handling
     * - API versioning
     * 
     * @param request The knowledge import request DTO
     * @param apiKey The user's API key for authentication
     * @param idempotencyKey Optional idempotency key for safe retries
     * @return ResponseEntity with processing status and estimated completion time
     */
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @RateLimit(key = "knowledge-import", limit = 10, window = 60, useApiKey = true)
    @Idempotent(keyParam = "idempotency_key", ttlSeconds = 3600, includeBody = true)
    public ResponseEntity<ApiResponse<KnowledgeImportResponse>> importFile(
            @Valid @RequestBody KnowledgeImportRequest request,
            @RequestParam("api_key") @NotBlank(message = "API key is required") String apiKey,
            @RequestParam(value = "idempotency_key", required = false) String idempotencyKey) {
        
        logger.info("Processing knowledge import request: file={}, name={}", 
                request.getFile(), request.getName());
        
        // Validate API key and get user ID
        String userId = userService.getUserIdFromApiKey(apiKey);
        if (userId == null) {
            logger.warn("Invalid API key provided: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
            throw new BusinessException("Invalid API key", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        // Validate file path and permissions
        validateFilePath(request.getFile());
        
        try {
            // Get file size for estimation
            long fileSize = getFileSize(request.getFile());
            
            // Generate knowledge base ID
            String knowledgeBaseId = "kb_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create knowledge base record with initial status
            String filename = Paths.get(request.getFile()).getFileName().toString();
            KnowledgeBase kb = new KnowledgeBase(knowledgeBaseId, userId, filename, "uploading");
            kb.setName(request.getName());
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                kb.setDescription(request.getDescription().trim());
            }
            kb.setFileSize(fileSize);
            
            knowledgeBaseRepository.save(kb);
            
            // Upload file to S3
            String s3Key = s3Service.generateS3Key(userId, knowledgeBaseId, filename);
            String s3Url = s3Service.uploadFile(request.getFile(), s3Key, userId, knowledgeBaseId);
            
            logger.info("Successfully uploaded file to S3: {} -> {}", request.getFile(), s3Url);
            
            // Update knowledge base status to pending processing
            kb.setStatus("pending");
            knowledgeBaseRepository.save(kb);
            
            // Create and send SQS message for processing
            KnowledgeProcessingMessage message = new KnowledgeProcessingMessage(
                    knowledgeBaseId, userId, s3Key, filename, fileSize);
            
            String messageId = sqsService.sendKnowledgeProcessingMessage(message);
            
            logger.info("Successfully sent processing message to SQS: messageId={}, knowledgeBaseId={}", 
                    messageId, knowledgeBaseId);
            
            // Calculate estimated completion time
            Instant estimatedCompletion = asyncProcessingService.estimateCompletionTime(fileSize);
            
            // Create response
            KnowledgeImportResponse response = new KnowledgeImportResponse(
                    knowledgeBaseId,
                    "pending",
                    "File uploaded successfully. Processing will begin shortly.",
                    estimatedCompletion
            );
            
            ApiResponse<KnowledgeImportResponse> apiResponse = ApiResponse.success(response);
            
            // Return 202 Accepted for async operations
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(apiResponse);
            
        } catch (Exception e) {
            logger.error("Error processing knowledge import: {}", e.getMessage(), e);
            throw new BusinessException("Failed to process file: " + e.getMessage(), 
                    "PROCESSING_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get processing status of a knowledge base
     * 
     * @param id The knowledge base ID
     * @param apiKey The user's API key
     * @return ResponseEntity with processing status
     */
    @GetMapping(value = "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @RateLimit(key = "knowledge-status", limit = 100, window = 60, useApiKey = true)
    public ResponseEntity<ApiResponse<KnowledgeImportResponse>> getProcessingStatus(
            @PathVariable @NotBlank(message = "Knowledge base ID is required") String id,
            @RequestParam("api_key") @NotBlank(message = "API key is required") String apiKey) {
        
        logger.debug("Getting processing status for knowledge base: {}", id);
        
        // Validate API key and get user ID
        String userId = userService.getUserIdFromApiKey(apiKey);
        if (userId == null) {
            logger.warn("Invalid API key provided for status check: {}", id);
            throw new BusinessException("Invalid API key", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        // Find knowledge base and check ownership
        var kbOptional = knowledgeBaseRepository.findById(id);
        if (kbOptional.isEmpty()) {
            logger.warn("Knowledge base not found for status check: {}", id);
            throw new BusinessException("Knowledge base not found", "NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        KnowledgeBase kb = kbOptional.get();
        
        if (!kb.getUserId().equals(userId)) {
            logger.warn("Access denied for status check: {} by user: {}", id, userId);
            throw new BusinessException("Access denied", "FORBIDDEN", HttpStatus.FORBIDDEN);
        }

        String status = asyncProcessingService.getProcessingStatus(id);
        
        // Create response based on status
        KnowledgeImportResponse response;
        switch (status) {
            case "completed":
                response = KnowledgeImportResponse.completed(id);
                break;
            case "failed":
                response = KnowledgeImportResponse.failed(id, "Processing failed");
                break;
            case "processing":
                response = KnowledgeImportResponse.processing(id, 2); // 2 minutes estimate
                break;
            default:
                response = new KnowledgeImportResponse(id, status, "Status: " + status, null);
        }
        
        logger.debug("Processing status for knowledge base {}: {}", id, status);
        
        ApiResponse<KnowledgeImportResponse> apiResponse = ApiResponse.success(response);
        
        return ResponseEntity.ok(apiResponse);
    }

    /**
     * Validate file path and check file accessibility
     */
    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new BusinessException("File path cannot be empty", "INVALID_FILE_PATH", HttpStatus.BAD_REQUEST);
        }
        
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new BusinessException("File not found: " + filePath, "FILE_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        
        if (!Files.isReadable(path)) {
            throw new BusinessException("File is not readable: " + filePath, "FILE_NOT_READABLE", HttpStatus.FORBIDDEN);
        }
        
        // Validate file extension
        String fileName = path.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".txt") && !fileName.endsWith(".pdf") && !fileName.endsWith(".md")) {
            throw new BusinessException("Unsupported file type. Only .txt, .pdf, and .md files are allowed", 
                    "UNSUPPORTED_FILE_TYPE", HttpStatus.BAD_REQUEST);
        }
        
        logger.debug("File validation passed for: {}", filePath);
    }
    
    /**
     * Get file size safely
     */
    private long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            logger.error("Error getting file size for: {}", filePath, e);
            throw new BusinessException("Unable to read file information", "FILE_READ_ERROR", HttpStatus.BAD_REQUEST);
        }
    }
} 