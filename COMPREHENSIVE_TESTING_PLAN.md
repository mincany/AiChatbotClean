# Comprehensive Testing Plan for AI Chatbot Backend

## 🎯 **Test Objectives**

This testing plan covers the complete end-to-end flow:
1. **User Registration** - Create user and get API key
2. **Knowledge Import** - Upload file and get knowledge base ID
3. **Async Processing** - Backend processes file and uploads to Pinecone
4. **RAG Chat Query** - Query with context from knowledge base

## 🚀 **Prerequisites & Setup**

### **1. Environment Setup**
Ensure all services are running and configured:

```bash
# Verify DynamoDB tables exist
aws dynamodb list-tables --region us-east-1

# Verify S3 bucket exists
aws s3 ls s3://chatbot-knowledge-files

# Verify SQS queue exists
aws sqs list-queues --region us-east-1
```

### **2. Application Configuration**
Your `application.properties` should have:
- ✅ OpenAI API key configured
- ✅ Pinecone API key and URL configured
- ✅ AWS credentials configured
- ✅ DynamoDB table names configured
- ✅ S3 bucket name configured
- ✅ SQS queue name configured

### **3. Start the Application**
```bash
# Start the Spring Boot application
mvn spring-boot:run

# Verify application is running
curl http://localhost:9090/api/v1/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "chatbot-api",
  "timestamp": "2024-01-01T12:00:00Z",
  "message": "Service is healthy and running"
}
```

### **4. Detailed Health Check**
```bash
curl http://localhost:9090/api/v1/health/detailed
```

Expected response should show all services as "UP":
```json
{
  "status": "UP",
  "service": "chatbot-api",
  "timestamp": "2024-01-01T12:00:00Z",
  "services": {
    "pinecone": {
      "status": "UP",
      "connection": "SUCCESS",
      "totalVectorCount": 0,
      "dimension": 1536
    }
  }
}
```

## 📋 **Test Plan Execution**

### **Test 1: User Registration**

#### **Objective**: Register a user and obtain API key

#### **Command**:
```bash
curl -X POST "http://localhost:9090/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com"
  }'
```

#### **Expected Response**:
```json
{
  "userId": "user_12345678",
  "apiKey": "ak_1234567890abcdef1234567890abcdef12345678",
  "email": "testuser@example.com",
  "message": "Registration successful. Save your API key securely."
}
```

#### **Verification Steps**:
1. ✅ Response status should be `200 OK`
2. ✅ Response should contain `userId`, `apiKey`, and `email`
3. ✅ API key should start with `ak_` and be 40+ characters
4. ✅ User ID should start with `user_` and be 13 characters

#### **Save Variables**:
```bash
# Save these for subsequent tests
export USER_ID="user_12345678"
export API_KEY="ak_1234567890abcdef1234567890abcdef12345678"
```

---

### **Test 2: Knowledge Base Import**

#### **Objective**: Upload a local file and get knowledge base ID

#### **Preparation**:
Create a test file:
```bash
cat > /tmp/test_knowledge.txt << 'EOF'
# Company Product Information

## Product Overview
Our flagship product is an AI-powered analytics platform that helps businesses make data-driven decisions. The platform includes advanced machine learning algorithms, real-time data processing, and intuitive dashboards.

## Key Features
- Real-time data analytics
- Machine learning predictions
- Custom dashboard creation
- API integrations
- Multi-tenant architecture
- Enterprise security

## Pricing
- Starter Plan: $99/month for up to 1,000 data points
- Professional Plan: $299/month for up to 10,000 data points
- Enterprise Plan: $999/month for unlimited data points

## Support
Our support team is available 24/7 via email, chat, and phone. We also provide comprehensive documentation and video tutorials.
EOF
```

#### **Command**:
```bash
curl -X POST "http://localhost:9090/api/v1/knowledge/import?api_key=${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "file": "/tmp/test_knowledge.txt",
    "name": "Company Product Information",
    "description": "Comprehensive information about our AI analytics platform"
  }'
```

#### **Expected Response**:
```json
{
  "success": true,
  "data": {
    "knowledgeBaseId": "kb_87654321",
    "status": "pending",
    "message": "File uploaded successfully. Processing will begin shortly.",
    "estimatedCompletion": "2024-01-01T12:05:00Z"
  }
}
```

#### **Verification Steps**:
1. ✅ Response status should be `202 Accepted`
2. ✅ Response should contain `knowledgeBaseId` starting with `kb_`
3. ✅ Status should be `pending`
4. ✅ Should include estimated completion time

#### **Save Variables**:
```bash
# Save for subsequent tests
export KNOWLEDGE_BASE_ID="kb_87654321"
```

---

### **Test 3: Monitor Processing Status**

#### **Objective**: Track async processing progress

#### **Command**:
```bash
curl -X GET "http://localhost:9090/api/v1/knowledge/${KNOWLEDGE_BASE_ID}/status?api_key=${API_KEY}"
```

#### **Expected Response Progression**:

**Initial Status (pending)**:
```json
{
  "success": true,
  "data": {
    "knowledgeBaseId": "kb_87654321",
    "status": "pending",
    "message": "Status: pending",
    "estimatedCompletion": null
  }
}
```

**Processing Status**:
```json
{
  "success": true,
  "data": {
    "knowledgeBaseId": "kb_87654321",
    "status": "processing",
    "message": "Status: processing",
    "estimatedCompletion": "2024-01-01T12:05:00Z"
  }
}
```

**Completed Status**:
```json
{
  "success": true,
  "data": {
    "knowledgeBaseId": "kb_87654321",
    "status": "completed",
    "message": "Status: completed",
    "estimatedCompletion": null
  }
}
```

#### **Verification Steps**:
1. ✅ Initial status should be `pending`
2. ✅ Status should progress to `processing`
3. ✅ Final status should be `completed`
4. ✅ Processing should complete within estimated time

#### **Monitoring Script**:
```bash
# Monitor processing status
while true; do
  STATUS=$(curl -s "http://localhost:9090/api/v1/knowledge/${KNOWLEDGE_BASE_ID}/status?api_key=${API_KEY}" | jq -r '.data.status')
  echo "$(date): Status = $STATUS"
  
  if [ "$STATUS" = "completed" ]; then
    echo "✅ Processing completed successfully!"
    break
  elif [ "$STATUS" = "failed" ]; then
    echo "❌ Processing failed!"
    break
  fi
  
  sleep 10
done
```

---

### **Test 4: RAG Chat Query**

#### **Objective**: Query the knowledge base using RAG with ChatGPT

#### **Test Queries**:

**Query 1: Product Features**
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}&top_k=3&score_threshold=0.5" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the key features of your AI analytics platform?",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'",
    "sessionId": "test_session_001"
  }'
```

**Query 2: Pricing Information**
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}&top_k=5&score_threshold=0.6" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "How much does the Professional Plan cost and what does it include?",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'",
    "sessionId": "test_session_001"
  }'
```

**Query 3: Support Information**
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What support options are available?",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'",
    "sessionId": "test_session_001"
  }'
```

#### **Expected Response Format**:
```json
{
  "success": true,
  "data": {
    "response": "Based on your document, the key features of your AI analytics platform include:\n\n1. Real-time data analytics\n2. Machine learning predictions\n3. Custom dashboard creation\n4. API integrations\n5. Multi-tenant architecture\n6. Enterprise security\n\nThese features make it a comprehensive solution for businesses looking to make data-driven decisions.",
    "knowledgeBaseId": "kb_87654321",
    "sessionId": "test_session_001",
    "sources": [
      {
        "chunkId": "kb_87654321_chunk_0",
        "documentName": "Company Product Information",
        "chunkIndex": 0,
        "relevanceScore": 0.89
      },
      {
        "chunkId": "kb_87654321_chunk_1",
        "documentName": "Company Product Information",
        "chunkIndex": 1,
        "relevanceScore": 0.76
      }
    ],
    "contextChunksUsed": 2,
    "minScore": 0.76,
    "maxScore": 0.89
  }
}
```

#### **Verification Steps**:
1. ✅ Response status should be `200 OK`
2. ✅ Response should contain relevant information from the uploaded document
3. ✅ Should include source information with chunk IDs and relevance scores
4. ✅ Context chunks should have scores above the threshold
5. ✅ AI response should be coherent and based on the document content

---

## 🔍 **Advanced Testing Scenarios**

### **Test 5: Error Handling**

#### **Invalid API Key**:
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=invalid_key" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Test question",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'"
  }'
```

Expected: `401 Unauthorized`

#### **Non-existent Knowledge Base**:
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Test question",
    "knowledgeBaseId": "kb_nonexistent"
  }'
```

Expected: `404 Not Found`

#### **Knowledge Base Not Ready**:
```bash
# Test immediately after upload before processing completes
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Test question",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'"
  }'
```

Expected: `412 Precondition Failed` if status is not "completed"

### **Test 6: Parameter Validation**

#### **Invalid top_k Parameter**:
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}&top_k=25" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Test question",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'"
  }'
```

Expected: `400 Bad Request` (top_k must be ≤ 20)

#### **Invalid score_threshold Parameter**:
```bash
curl -X POST "http://localhost:9090/api/v1/chat/query?api_key=${API_KEY}&score_threshold=1.5" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Test question",
    "knowledgeBaseId": "'${KNOWLEDGE_BASE_ID}'"
  }'
```

Expected: `400 Bad Request` (score_threshold must be ≤ 1.0)

---

## 🚨 **Known Issues & Missing Components**

Based on my codebase analysis, here are the issues that need attention:

### **1. Critical Issues**

#### **Missing SQS Configuration Bean**
- **Issue**: `SqsClient` bean is not configured in any configuration class
- **Impact**: Knowledge import will fail when trying to send SQS messages
- **Fix Needed**: Add SQS client configuration to `AwsConfig.java`

#### **Incomplete KnowledgeController Implementation**
- **Issue**: The controller references `s3Service` and `sqsService` but the current implementation uses direct file processing
- **Impact**: The async processing flow is inconsistent
- **Fix Needed**: Choose between S3+SQS approach or direct file processing

#### **Missing Spring Cloud AWS SQS Dependency**
- **Issue**: `@SqsListener` annotation requires Spring Cloud AWS SQS dependency
- **Impact**: SQS message processing won't work
- **Fix Needed**: Add dependency to `pom.xml`

### **2. Configuration Issues**

#### **Hardcoded Status in KnowledgeBase Constructor**
- **Issue**: Constructor sets status to "pending" but code expects "uploading"
- **Impact**: Status tracking inconsistency
- **Fix Needed**: Align status values

#### **Missing Username Support in Registration**
- **Issue**: Registration only accepts email, but User model has username field
- **Impact**: Username field remains null
- **Fix Needed**: Update RegisterRequest to include username

### **3. Potential Runtime Issues**

#### **File Path Validation**
- **Issue**: Knowledge import expects absolute file paths
- **Impact**: Users need to provide full system paths
- **Recommendation**: Consider supporting relative paths or file upload

#### **Pinecone Index Initialization**
- **Issue**: Index creation happens synchronously on startup
- **Impact**: Application startup may be slow or fail if Pinecone is unavailable
- **Recommendation**: Make index initialization more resilient

---

## 🛠️ **Quick Fixes Required**

### **1. Add SQS Client Configuration**
Add to `AwsConfig.java`:
```java
@Bean
public SqsClient sqsClient() {
    SqsClientBuilder builder = SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create());
    return builder.build();
}
```

### **2. Add Spring Cloud AWS SQS Dependency**
Add to `pom.xml`:
```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-sqs</artifactId>
    <version>3.0.0</version>
</dependency>
```

### **3. Fix Status Consistency**
Update KnowledgeBase constructor:
```java
public KnowledgeBase(String knowledgeId, String userId, String fileName, String status) {
    this();
    this.knowledgeId = knowledgeId;
    this.userId = userId;
    this.fileName = fileName;
    this.status = "pending"; // Changed from "uploading"
    this.pineconeNamespace = knowledgeId;
}
```

---

## 📊 **Success Criteria**

### **Complete Test Success Indicators**:
1. ✅ User registration returns valid API key
2. ✅ Knowledge import returns knowledge base ID
3. ✅ Processing status progresses: pending → processing → completed
4. ✅ Chat queries return relevant responses with source information
5. ✅ Error handling works correctly for invalid inputs
6. ✅ Pinecone contains document chunks in user namespace
7. ✅ OpenAI generates contextual responses based on document content

### **Performance Benchmarks**:
- User registration: < 2 seconds
- Knowledge import: < 5 seconds (for file upload)
- Processing completion: < 2 minutes (for small files)
- Chat query response: < 10 seconds

### **Data Verification**:
```bash
# Verify user in DynamoDB
aws dynamodb get-item --table-name chatbot-users --key '{"UserId":{"S":"'${USER_ID}'"}}'

# Verify knowledge base in DynamoDB
aws dynamodb get-item --table-name chatbot-knowledge --key '{"KnowledgeId":{"S":"'${KNOWLEDGE_BASE_ID}'"}}'

# Verify file in S3 (if using S3 approach)
aws s3 ls s3://chatbot-knowledge-files/ --recursive
```

---

## 🎯 **Conclusion**

This testing plan provides comprehensive coverage of the RAG chatbot functionality. The main issues to address are the SQS configuration and dependency management. Once these are fixed, the system should work end-to-end as designed.

The architecture supports:
- ✅ User namespace isolation in Pinecone
- ✅ Smart document chunking
- ✅ Top-K similarity search with scoring
- ✅ Enhanced query processing with keyword extraction
- ✅ Comprehensive error handling and validation
- ✅ Rate limiting and idempotency

Follow this testing plan step-by-step to validate the complete RAG implementation!
