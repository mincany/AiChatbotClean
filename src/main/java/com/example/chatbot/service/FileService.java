package com.example.chatbot.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class FileService {

    private final Tika tika;
    private final List<String> supportedMimeTypes = Arrays.asList(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/markdown"
    );

    public FileService() {
        this.tika = new Tika();
    }

    public String extractText(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size (10MB limit)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        // Detect content type
        String contentType = tika.detect(file.getInputStream());
        
        // Check if supported
        if (!supportedMimeTypes.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        // Extract text
        try {
            String text = tika.parseToString(file.getInputStream());
            
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException("No text content found in file");
            }
            
            return text.trim();
        } catch (Exception e) {
            throw new IOException("Failed to extract text from file: " + e.getMessage(), e);
        }
    }

    public boolean isValidFileType(MultipartFile file) {
        try {
            String contentType = tika.detect(file.getInputStream());
            return supportedMimeTypes.contains(contentType);
        } catch (IOException e) {
            return false;
        }
    }
} 