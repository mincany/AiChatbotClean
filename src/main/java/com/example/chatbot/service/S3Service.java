package com.example.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final S3Client s3Client;
    private final String bucketName;

    public S3Service(@Value("${aws.s3.bucket-name}") String bucketName,
                    @Value("${aws.s3.region}") String region,
                    @Value("${aws.access-key-id:}") String accessKey,
                    @Value("${aws.secret-access-key:}") String secretKey) {
        this.bucketName = bucketName;
        this.s3Client = createS3Client(region, accessKey, secretKey);
        
        // Ensure bucket exists
        ensureBucketExists();
    }

    private S3Client createS3Client(String region, String accessKey, String secretKey) {
        var builder = S3Client.builder().region(Region.of(region));

        // Use provided credentials if available, otherwise use default provider chain
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
            builder.credentialsProvider(credentialsProvider);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private void ensureBucketExists() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
            logger.info("S3 bucket exists: {}", bucketName);
        } catch (NoSuchBucketException e) {
            logger.info("Creating S3 bucket: {}", bucketName);
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.createBucket(createBucketRequest);
            logger.info("Successfully created S3 bucket: {}", bucketName);
        } catch (Exception e) {
            logger.error("Error checking/creating S3 bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to initialize S3 bucket", e);
        }
    }

    /**
     * Upload file from local file system to S3
     * 
     * @param localFilePath Path to the local file
     * @param s3Key S3 object key (path in bucket)
     * @param userId User ID for metadata
     * @param knowledgeBaseId Knowledge base ID for metadata
     * @return S3 object URL
     */
    public String uploadFile(String localFilePath, String s3Key, String userId, String knowledgeBaseId) {
        try {
            Path filePath = Paths.get(localFilePath);
            
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("File not found: " + localFilePath);
            }

            // Prepare metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("user-id", userId);
            metadata.put("knowledge-base-id", knowledgeBaseId);
            metadata.put("upload-timestamp", Instant.now().toString());
            metadata.put("original-filename", filePath.getFileName().toString());

            // Upload file
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .metadata(metadata)
                    .contentType(getContentType(filePath.getFileName().toString()))
                    .build();

            PutObjectResponse response = s3Client.putObject(putObjectRequest, 
                    RequestBody.fromFile(filePath));

            String s3Url = String.format("s3://%s/%s", bucketName, s3Key);
            logger.info("Successfully uploaded file to S3: {} -> {}", localFilePath, s3Url);
            
            return s3Url;

        } catch (Exception e) {
            logger.error("Error uploading file to S3: {} -> {}", localFilePath, s3Key, e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Upload MultipartFile directly to S3
     * 
     * @param multipartFile MultipartFile from form upload
     * @param s3Key S3 object key (path in bucket)
     * @param userId User ID for metadata
     * @param knowledgeBaseId Knowledge base ID for metadata
     * @return S3 object URL
     */
    public String uploadFile(MultipartFile multipartFile, String s3Key, String userId, String knowledgeBaseId) {
        try {
            if (multipartFile.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }

            // Prepare metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("user-id", userId);
            metadata.put("knowledge-base-id", knowledgeBaseId);
            metadata.put("upload-timestamp", Instant.now().toString());
            metadata.put("original-filename", multipartFile.getOriginalFilename());
            metadata.put("content-type", multipartFile.getContentType());

            // Create put request
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(multipartFile.getContentType())
                    .metadata(metadata)
                    .build();

            // Upload file
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    multipartFile.getInputStream(), multipartFile.getSize()));

            String s3Url = String.format("s3://%s/%s", bucketName, s3Key);
            logger.info("Successfully uploaded multipart file to S3: {} -> {}", 
                    multipartFile.getOriginalFilename(), s3Key);
            
            return s3Url;

        } catch (Exception e) {
            logger.error("Error uploading multipart file to S3: {} -> {}", 
                    multipartFile.getOriginalFilename(), s3Key, e);
            throw new RuntimeException("Failed to upload multipart file to S3", e);
        }
    }

    /**
     * Download file from S3 to local temporary file
     * 
     * @param s3Key S3 object key
     * @return Path to downloaded temporary file
     */
    public Path downloadFile(String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            
            // Create temporary file
            String originalFilename = getOriginalFilename(s3Key);
            String extension = getFileExtension(originalFilename);
            Path tempFile = Files.createTempFile("s3-download-", extension);
            
            // Copy S3 content to temporary file
            Files.copy(s3Object, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Successfully downloaded file from S3: {} -> {}", s3Key, tempFile);
            
            return tempFile;

        } catch (Exception e) {
            logger.error("Error downloading file from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }

    /**
     * Download file content as byte array
     * 
     * @param s3Key S3 object key
     * @return File content as byte array
     */
    public byte[] downloadFileAsBytes(String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            byte[] content = s3Object.readAllBytes();
            
            logger.debug("Downloaded file content from S3: {} ({} bytes)", s3Key, content.length);
            
            return content;

        } catch (Exception e) {
            logger.error("Error downloading file content from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to download file content from S3", e);
        }
    }

    /**
     * Delete file from S3
     * 
     * @param s3Key S3 object key
     */
    public void deleteFile(String s3Key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            logger.info("Successfully deleted file from S3: {}", s3Key);

        } catch (Exception e) {
            logger.error("Error deleting file from S3: {}", s3Key, e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    /**
     * Check if file exists in S3
     * 
     * @param s3Key S3 object key
     * @return true if file exists
     */
    public boolean fileExists(String s3Key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            logger.error("Error checking file existence in S3: {}", s3Key, e);
            throw new RuntimeException("Failed to check file existence in S3", e);
        }
    }

    /**
     * Generate S3 key for knowledge base file
     * 
     * @param userId User ID
     * @param knowledgeBaseId Knowledge base ID
     * @param filename Original filename
     * @return S3 key
     */
    public String generateS3Key(String userId, String knowledgeBaseId, String filename) {
        return String.format("knowledge-bases/%s/%s/%s", userId, knowledgeBaseId, filename);
    }

    private String getContentType(String filename) {
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFilename.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerFilename.endsWith(".md")) {
            return "text/markdown";
        }
        return "application/octet-stream";
    }

    private String getOriginalFilename(String s3Key) {
        // Extract filename from S3 key
        String[] parts = s3Key.split("/");
        return parts[parts.length - 1];
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex);
        }
        return "";
    }
}
