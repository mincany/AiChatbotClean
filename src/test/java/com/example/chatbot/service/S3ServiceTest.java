package com.example.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3Service focusing on uploadFile and downloadFile methods.
 * 
 * This test class demonstrates proper mocking of AWS S3Client to test
 * business logic without making actual AWS calls.
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client mockS3Client;

    @Mock
    private PutObjectResponse mockPutObjectResponse;
    
    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseInputStream;

    private S3Service s3Service;

    // Test constants
    private static final String TEST_BUCKET_NAME = "test-chatbot-bucket";
    private static final String TEST_REGION = "us-east-1";
    private static final String TEST_USER_ID = "user123";
    private static final String TEST_KNOWLEDGE_BASE_ID = "kb_12345678";
    private static final String TEST_FILENAME = "test-document.txt";
    private static final String TEST_S3_KEY = "knowledge-bases/user123/kb_12345678/test-document.txt";

    /**
     * Setup method that creates S3Service instance and injects mocked S3Client.
     * This avoids the constructor AWS calls by using reflection.
     */
    @BeforeEach
    void setUp() {
        // Create S3Service instance using reflection to avoid constructor calls
        s3Service = mock(S3Service.class, CALLS_REAL_METHODS);
        
        // Inject the mocked S3Client and required fields
        ReflectionTestUtils.setField(s3Service, "s3Client", mockS3Client);
        ReflectionTestUtils.setField(s3Service, "bucketName", TEST_BUCKET_NAME);
    }

    /**
     * Test successful file upload from local file system to S3.
     * 
     * Verifies that:
     * 1. S3Client.putObject is called with correct parameters
     * 2. Proper metadata is set (user ID, knowledge base ID, filename)
     * 3. Content type is correctly determined
     * 4. Correct S3 URL is returned
     */
    @Test
    void testUploadFile_FromLocalPath_Success(@TempDir Path tempDir) throws IOException {
        // Arrange: Create a temporary test file
        Path testFile = tempDir.resolve(TEST_FILENAME);
        String testContent = "This is test content for S3 upload";
        Files.write(testFile, testContent.getBytes());

        // Mock S3Client response for successful upload
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(mockPutObjectResponse);

        // Act: Call the method under test
        String result = s3Service.uploadFile(
            testFile.toString(), 
            TEST_S3_KEY, 
            TEST_USER_ID, 
            TEST_KNOWLEDGE_BASE_ID
        );

        // Assert: Verify the result
        String expectedS3Url = "s3://" + TEST_BUCKET_NAME + "/" + TEST_S3_KEY;
        assertEquals(expectedS3Url, result, "Should return correct S3 URL");

        // Verify S3Client.putObject was called exactly once
        verify(mockS3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // Capture and verify the PutObjectRequest parameters
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        
        // Verify basic request parameters
        assertEquals(TEST_BUCKET_NAME, capturedRequest.bucket(), "Should use correct bucket name");
        assertEquals(TEST_S3_KEY, capturedRequest.key(), "Should use correct S3 key");
        assertEquals("text/plain", capturedRequest.contentType(), "Should set correct content type for .txt files");
        
        // Verify metadata contains all required fields
        assertNotNull(capturedRequest.metadata(), "Metadata should not be null");
        assertEquals(TEST_USER_ID, capturedRequest.metadata().get("user-id"), "Should include user ID in metadata");
        assertEquals(TEST_KNOWLEDGE_BASE_ID, capturedRequest.metadata().get("knowledge-base-id"), "Should include knowledge base ID in metadata");
        assertEquals(TEST_FILENAME, capturedRequest.metadata().get("original-filename"), "Should include original filename in metadata");
        assertNotNull(capturedRequest.metadata().get("upload-timestamp"), "Should include upload timestamp in metadata");
    }

    /**
     * Test successful MultipartFile upload to S3.
     * 
     * Verifies that the overloaded uploadFile method works correctly
     * with MultipartFile input (common in web applications).
     */
    @Test
    void testUploadFile_FromMultipartFile_Success() throws IOException {
        // Arrange: Create a mock MultipartFile
        String testContent = "This is test content from multipart file";
        MockMultipartFile mockFile = new MockMultipartFile(
            "file", 
            TEST_FILENAME, 
            "text/plain", 
            testContent.getBytes()
        );

        // Mock S3Client response
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(mockPutObjectResponse);

        // Act: Upload the multipart file
        String result = s3Service.uploadFile(mockFile, TEST_S3_KEY, TEST_USER_ID, TEST_KNOWLEDGE_BASE_ID);

        // Assert: Verify result
        String expectedS3Url = "s3://" + TEST_BUCKET_NAME + "/" + TEST_S3_KEY;
        assertEquals(expectedS3Url, result, "Should return correct S3 URL for multipart upload");

        // Verify S3Client interaction
        verify(mockS3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // Capture and verify request parameters
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_BUCKET_NAME, capturedRequest.bucket());
        assertEquals(TEST_S3_KEY, capturedRequest.key());
        assertEquals("text/plain", capturedRequest.contentType());
        
        // Verify multipart-specific metadata
        assertEquals(TEST_FILENAME, capturedRequest.metadata().get("original-filename"));
        assertEquals("text/plain", capturedRequest.metadata().get("content-type"));
    }

    /**
     * Test successful file download as byte array from S3.
     * 
     * Verifies that:
     * 1. S3Client.getObject is called with correct parameters
     * 2. File content is properly returned as byte array
     * 3. Correct bucket and key are used in the request
     */
    @Test
    void testDownloadFileAsBytes_Success() throws IOException {
        // Arrange: Mock S3 response
        String testContent = "Test content downloaded from S3";
        byte[] expectedBytes = testContent.getBytes();
        
        when(mockS3Client.getObject(any(GetObjectRequest.class)))
            .thenReturn(mockResponseInputStream);
        when(mockResponseInputStream.readAllBytes())
            .thenReturn(expectedBytes);

        // Act: Download file content as bytes
        byte[] result = s3Service.downloadFileAsBytes(TEST_S3_KEY);

        // Assert: Verify content
        assertArrayEquals(expectedBytes, result, "Should return correct file content as bytes");

        // Verify S3Client.getObject was called exactly once
        verify(mockS3Client, times(1)).getObject(any(GetObjectRequest.class));

        // Capture and verify the GetObjectRequest parameters
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(mockS3Client).getObject(requestCaptor.capture());

        GetObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_BUCKET_NAME, capturedRequest.bucket(), "Should use correct bucket name");
        assertEquals(TEST_S3_KEY, capturedRequest.key(), "Should use correct S3 key");
    }

    /**
     * Test upload failure when local file doesn't exist.
     * 
     * Verifies proper error handling without making S3 calls.
     */
    @Test
    void testUploadFile_FileNotFound_ThrowsException() {
        // Arrange: Use a non-existent file path
        String nonExistentPath = "/path/that/does/not/exist/file.txt";

        // Act & Assert: Verify exception is thrown
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.uploadFile(nonExistentPath, TEST_S3_KEY, TEST_USER_ID, TEST_KNOWLEDGE_BASE_ID);
        });

        assertTrue(exception.getMessage().contains("Failed to upload file to S3"), 
            "Should throw RuntimeException with appropriate message");
        
        // Verify S3Client was never called since file validation failed
        verify(mockS3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    /**
     * Test download failure when S3 throws exception.
     * 
     * Verifies proper error handling when S3 operations fail.
     */
    @Test
    void testDownloadFileAsBytes_S3Exception_ThrowsRuntimeException() {
        // Arrange: Mock S3Client to throw exception
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .message("S3 access denied")
            .statusCode(403)
            .build();
        when(mockS3Client.getObject(any(GetObjectRequest.class)))
            .thenThrow(s3Exception);

        // Act & Assert: Verify exception handling
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            s3Service.downloadFileAsBytes(TEST_S3_KEY);
        });

        assertTrue(exception.getMessage().contains("Failed to download file content from S3"));
        assertTrue(exception.getCause() instanceof S3Exception);
        
        // Verify S3Client was called (but threw exception)
        verify(mockS3Client, times(1)).getObject(any(GetObjectRequest.class));
    }
}