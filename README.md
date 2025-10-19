# AI Chatbot Backend POC

A production-ready Spring Boot application that provides a REST API for an AI-powered chatbot with document ingestion and query capabilities using AWS SQS for asynchronous processing.

## Features

- **User Registration**: Create users with API keys
- **Knowledge Import**: Upload and process text documents (TXT, PDF, MD) via S3 and SQS
- **AI-Powered Chat**: Ask questions about uploaded documents using OpenAI
- **Vector Search**: Uses Pinecone for semantic search of document content
- **Asynchronous Processing**: SQS-based background processing for scalability
- **File Storage**: AWS S3 for reliable file storage
- **Rate Limiting**: Built-in rate limiting and idempotency
- **Comprehensive Monitoring**: Health checks and status tracking

## Prerequisites

- Java 17+
- Maven 3.6+
- AWS Account (S3, SQS, DynamoDB) OR LocalStack for development
- OpenAI API key
- Pinecone API key and index

## Quick Start

### Option 1: Local Development with LocalStack (Recommended)

```bash
# 1. Set up LocalStack and AWS services
./setup-localstack.sh

# 2. Copy and configure environment variables
cp environment-template.txt .env
# Edit .env with your OpenAI and Pinecone API keys

# 3. Run the application
mvn spring-boot:run

# 4. Verify everything works
./verify-setup.sh
```

### Option 2: Production AWS Setup

See `DEPLOYMENT_SETUP_GUIDE.md` for complete production setup instructions.

## Setup Files

- **`DEPLOYMENT_SETUP_GUIDE.md`** - Complete setup guide for all environments
- **`environment-template.txt`** - Environment variables template
- **`setup-localstack.sh`** - Automated LocalStack setup script
- **`verify-setup.sh`** - System verification script
- **`SQS_INTEGRATION_GUIDE.md`** - Detailed SQS architecture documentation

## API Usage

### 1. Register User

```bash
curl -X POST http://localhost:9090/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
```

Response:
```json
{
  "userId": "user_12345678",
  "apiKey": "ak_abcdef123456...",
  "email": "test@example.com",
  "message": "Registration successful. Save your API key securely."
}
```

### 2. Upload Document

```bash
curl -X POST http://localhost:9090/api/v1/knowledge/import \
  -F "file=@document.txt" \
  -F "api_key=ak_your_api_key_here" \
  -F "name=My Document" \
  -F "description=A test document"
```

Response:
```json
{
  "knowledge_base_id": "kb_87654321",
  "status": "processed",
  "message": "File processed successfully"
}
```

### 3. Ask Question

```bash
curl -X POST http://localhost:9090/api/v1/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "knowledgeBaseId": "kb_87654321",
    "question": "What is this document about?"
  }' \
  -G -d "api_key=ak_your_api_key_here"
```

Response:
```json
{
  "response": "Based on the document, this appears to be about...",
  "knowledgeBaseId": "kb_87654321"
}
```

## Configuration

Key configuration properties in `application.properties`:

- `server.port=9090` - Server port
- `openai.api-key` - OpenAI API key
- `pinecone.api-key` - Pinecone API key
- `pinecone.api-url` - Pinecone index URL
- `aws.dynamodb.endpoint` - DynamoDB endpoint (local: http://localhost:8000)

## Supported File Types

- Plain text (.txt)
- PDF (.pdf)
- Word documents (.docx)
- Markdown (.md)

Maximum file size: 10MB

## Architecture

- **Spring Boot** - REST API framework
- **DynamoDB** - User and knowledge base metadata storage
- **OpenAI** - Text embeddings and chat completions
- **Pinecone** - Vector database for semantic search
- **Apache Tika** - Document text extraction

## Development Notes

This is a POC implementation with simplified:
- Authentication (API key in request parameters)
- Error handling (basic try-catch blocks)
- File processing (synchronous, no chunking)
- Vector storage (single vector per document)

For production use, consider:
- JWT-based authentication
- Async file processing with queues
- Document chunking for large files
- Enhanced error handling and logging
- Rate limiting and input validation 