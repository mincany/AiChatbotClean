# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an AI Chatbot Backend POC built with Spring Boot. It provides a REST API for an AI-powered chatbot with document ingestion and query capabilities using OpenAI for embeddings/chat and Pinecone for vector search.

## Common Development Commands

### Build and Run
```bash
# Run the application (starts on port 9090)
mvn spring-boot:run

# Build the project
mvn clean compile

# Run tests
mvn test

# Package the application
mvn clean package
```

### Local Development Setup
```bash
# Start local DynamoDB (required for development)
docker run -p 8000:8000 amazon/dynamodb-local

# Create required DynamoDB tables
aws dynamodb create-table \
  --table-name chatbot-users \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000

aws dynamodb create-table \
  --table-name chatbot-knowledge \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000
```

### Required Environment Variables
```bash
export OPENAI_API_KEY="your-openai-api-key"
export PINECONE_API_KEY="your-pinecone-api-key"
export PINECONE_API_URL="your-pinecone-index-url"
```

## Architecture

### Core Components
- **Controllers**: Handle REST API endpoints (`/api/v1/auth`, `/api/v1/chat`, `/api/v1/knowledge`)
- **Services**: Business logic for OpenAI integration, Pinecone vector operations, file processing, and user management
- **Repositories**: DynamoDB data access for users and knowledge bases
- **DTOs**: Request/response objects for API communication
- **Models**: Entity classes for User and KnowledgeBase

### Key Services
- `OpenAiService`: Handles embeddings creation and chat completions using OpenAI API
- `PineconeService`: Manages vector storage and similarity search
- `FileService`: Processes document uploads (TXT, PDF, DOCX, MD) using Apache Tika
- `UserService`: Manages user registration and API key validation

### Data Flow
1. User registers and receives API key
2. Documents are uploaded, processed with Tika, embedded with OpenAI, and stored in Pinecone
3. Chat queries are embedded, searched in Pinecone for context, and answered using OpenAI with retrieved context

### Configuration Files
- `application.properties`: Contains server port (9090), database endpoints, API configurations
- `pom.xml`: Maven dependencies including Spring Boot, AWS SDK, Apache Tika, Jackson

### Authentication
Uses simple API key authentication passed as query parameter `api_key`. Keys are stored in DynamoDB users table.

## File Processing
- Supports TXT, PDF, DOCX, MD files up to 10MB
- Uses Apache Tika for text extraction
- Creates single embedding per document (no chunking in this POC)

## API Endpoints

### 1. User Registration API
**Endpoint**: `POST /api/v1/auth/register`
**Functionality**:
- Accept email
- Return an API key and success message
- Vector embedding generation for semantic search

### 2. Knowledge Import API
**Endpoint**: `POST /api/v1/knowledge/import`
**Functionality**:
- Accept text file uploads (TXT, PDF, DOCX, MD)
- Generate unique knowledge base IDs for isolation
- Vector embedding generation for semantic search

### 3. Chat Query API
**Endpoint**: `POST /api/v1/chat/query`
**Functionality**:
- Accept user questions with knowledge base ID
- Retrieve relevant context from knowledge base
- Send context + question to OpenAI API
- Return AI-generated response based on uploaded knowledge

## When making commit
NEVER ADD 
Generated with [Claude                      │
│   Code](https://claude.ai/code)                  │
│                                                  │
│   Co-Authored-By: Claude                         │
│   <noreply@anthropic.com>"                       │
│   Create initial commit with project             │
│   description      