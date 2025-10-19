package com.example.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class KnowledgeImportRequest {
    
    @JsonProperty("file")
    @NotBlank(message = "File path is required")
    @Pattern(regexp = ".*\\.(txt|pdf|md)$", message = "File must be .txt, .pdf, or .md format")
    private String file;
    
    @JsonProperty("name")
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    private String name;
    
    @JsonProperty("description")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    // Default constructor
    public KnowledgeImportRequest() {}

    // Constructor
    public KnowledgeImportRequest(String file, String name, String description) {
        this.file = file;
        this.name = name;
        this.description = description;
    }

    // Getters and Setters
    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
