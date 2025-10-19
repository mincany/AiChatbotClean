# LLM Reranking and Content Guardrails Implementation

## Overview

This implementation provides a simple yet effective LLM-based reranking system and comprehensive content guardrails with observability features designed for CloudWatch integration.

## 🔄 LLM-Based Reranking

### Simple and Effective Approach

Instead of complex hybrid algorithms, this implementation uses OpenAI's LLM to directly score the relevance between queries and context chunks.

#### Key Features:
- **Direct LLM Scoring**: Uses GPT-3.5-turbo to score relevance (0-10 scale)
- **Lightweight**: Minimal additional complexity over basic retrieval
- **Contextual Understanding**: Leverages LLM's natural language understanding
- **Fallback Handling**: Graceful degradation when LLM scoring fails

#### How It Works:
1. Retrieve initial chunks from Pinecone (2x `top_k` if reranking enabled)
2. For each chunk, ask LLM: "How relevant is this context to answering the query? Score 0-10"
3. Combine LLM score (70%) with original Pinecone score (30%)
4. Sort by combined score and return top results

### API Usage

```bash
curl -X POST "http://localhost:9090/api/v1/chat/query" \
  -H "Content-Type: application/json" \
  -d '{
    "knowledgeBaseId": "kb-123",
    "question": "What are the benefits of machine learning?"
  }' \
  --data-urlencode "api_key=your-key" \
  --data-urlencode "top_k=5" \
  --data-urlencode "enable_reranking=true"
```

### Performance Considerations

- **Token Usage**: Each chunk requires ~10 tokens for scoring
- **Latency**: Adds ~200-500ms per chunk (parallelizable)
- **Cost**: Minimal additional cost (~$0.0001 per chunk)
- **Accuracy**: Significant improvement in relevance ranking

## 🛡️ Content Guardrails

### Comprehensive Policy Enforcement

The `ContentGuardrailsService` provides multi-layered content filtering:

#### 1. Toxic Content Detection
- **Profanity filtering**: Common offensive language
- **Hate speech detection**: Discriminatory and threatening language
- **Threat identification**: Violence and harm indicators

#### 2. Confidential Data Protection
- **PII Detection**: SSN, credit cards, phone numbers, emails
- **Sensitive Patterns**: API keys, passwords, IP addresses
- **Business Data**: Employee IDs, customer codes, internal references

#### 3. Policy Enforcement Levels
```java
public enum PolicySeverity {
    LOW,      // Warning logged, content allowed
    MEDIUM,   // Content flagged, user notified
    HIGH,     // Content blocked, detailed logging
    CRITICAL  // Request rejected, security alert
}
```

### Content Types Monitored
- **User Queries**: Input validation and threat detection
- **AI Responses**: Output filtering and safety checks
- **Context Chunks**: Knowledge base content validation
- **Knowledge Uploads**: Document screening before processing

### Example Patterns Detected

```java
// Confidential data patterns
"SSN": "\\b\\d{3}-\\d{2}-\\d{4}\\b"
"CREDIT_CARD": "\\b(?:\\d{4}[- ]?){3}\\d{4}\\b"
"API_KEY": "\\b[A-Za-z0-9]{32,}\\b"

// Business-specific patterns (customizable)
"EMPLOYEE_ID": "\\bEMP\\d{6}\\b"
"CUSTOMER_ID": "\\bCUST\\d{8}\\b"
```

## 📊 Observability and Logging

### CloudWatch-Ready Structured Logging

All logging is designed for seamless CloudWatch integration with structured JSON format and proper log levels.

#### Log Categories:

1. **Chat Query Events**
```json
{
  "eventType": "CHAT_QUERY",
  "timestamp": "2024-01-15T10:30:00Z",
  "userId": "user-123",
  "requestId": "req-456",
  "knowledgeBaseId": "kb-789",
  "questionLength": 45,
  "topK": 5,
  "rerankingEnabled": true
}
```

2. **Context Usage Events**
```json
{
  "eventType": "CONTEXT_USAGE",
  "userId": "user-123",
  "chunksRetrieved": 10,
  "chunksUsed": 5,
  "totalContextLength": 2048,
  "avgScore": 0.85,
  "rerankingApplied": true,
  "chunks": [...]
}
```

3. **AI Response Events**
```json
{
  "eventType": "AI_RESPONSE",
  "userId": "user-123",
  "model": "gpt-3.5-turbo",
  "tokensUsed": 150,
  "processingTimeMs": 1200,
  "responseLength": 256
}
```

4. **Error Events**
```json
{
  "eventType": "ERROR",
  "userId": "user-123",
  "errorType": "BusinessException",
  "errorCode": "CONTENT_POLICY_VIOLATION",
  "errorMessage": "Toxic language detected",
  "context": {...}
}
```

5. **Performance Metrics**
```json
{
  "eventType": "PERFORMANCE",
  "operation": "chat_query",
  "durationMs": 2500,
  "success": true,
  "metadata": {...}
}
```

### CloudWatch Integration

#### Log Groups Structure:
```
/aws/lambda/chatbot-api/application     # Main application logs
/aws/lambda/chatbot-api/metrics         # Performance metrics
/aws/lambda/chatbot-api/audit          # Compliance and security events
/aws/lambda/chatbot-api/guardrails     # Content policy violations
```

#### Custom Metrics:
```
METRIC_CHAT_QUERY userId=user-123 topK=5 rerankingEnabled=true
METRIC_CONTEXT_USAGE userId=user-123 chunksUsed=5 avgScore=0.85
METRIC_AI_RESPONSE userId=user-123 tokensUsed=150 processingTimeMs=1200
METRIC_ERROR userId=user-123 errorType=BusinessException
METRIC_PERFORMANCE operation=chat_query durationMs=2500 success=true
GUARDRAILS_METRIC userId=user-123 contentType=USER_QUERY valid=false violations=1
```

### MDC (Mapped Diagnostic Context)

Each request includes contextual information:
```java
MDC.put("userId", userId);
MDC.put("sessionId", sessionId);
MDC.put("requestId", requestId);
MDC.put("knowledgeBaseId", knowledgeBaseId);
```

## 🔧 Configuration

### Application Properties

```properties
# Logging configuration for CloudWatch
logging.level.com.example.chatbot=INFO
logging.level.METRICS=INFO
logging.level.AUDIT=WARN
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{userId},%X{requestId}] %logger{36} - %msg%n

# Content guardrails
content.guardrails.enabled=true
content.guardrails.strict-mode=false
content.guardrails.log-violations=true

# LLM reranking
llm.reranking.enabled=true
llm.reranking.max-chunks=20
llm.reranking.timeout-ms=5000
```

### CloudWatch Log Configuration

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <mdc/>
                <message/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>
    
    <logger name="METRICS" level="INFO" additivity="false">
        <appender-ref ref="STDOUT"/>
    </logger>
    
    <logger name="AUDIT" level="WARN" additivity="false">
        <appender-ref ref="STDOUT"/>
    </logger>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## 📈 Monitoring and Alerts

### CloudWatch Dashboards

#### Key Metrics to Monitor:
1. **Request Volume**: Total chat queries per minute/hour
2. **Response Times**: P50, P95, P99 latencies
3. **Error Rates**: Failed requests by error type
4. **Content Violations**: Policy violations by severity
5. **Reranking Performance**: Accuracy improvements
6. **Token Usage**: OpenAI API consumption

#### Sample CloudWatch Queries:
```sql
-- Request volume by user
fields @timestamp, userId, eventType
| filter eventType = "CHAT_QUERY"
| stats count() by userId
| sort count desc

-- Average response times
fields @timestamp, durationMs
| filter eventType = "PERFORMANCE" and operation = "chat_query"
| stats avg(durationMs), max(durationMs), min(durationMs) by bin(5m)

-- Content policy violations
fields @timestamp, userId, violations
| filter eventType = "GUARDRAILS_METRIC" and valid = false
| stats count() by violations
```

### Alerting Rules

#### Critical Alerts:
- **High Error Rate**: >5% errors in 5 minutes
- **Content Policy Violations**: >10 violations in 1 hour
- **Response Time Degradation**: P95 > 5 seconds
- **Token Usage Spike**: >200% increase from baseline

#### Warning Alerts:
- **Reranking Failures**: >10% reranking failures
- **Context Quality Drop**: Average score < 0.7
- **Unusual User Activity**: >100 requests/hour per user

## 🧪 Testing

### Content Guardrails Testing

```bash
# Test toxic content detection
curl -X POST "http://localhost:9090/api/v1/chat/query" \
  -d '{"knowledgeBaseId":"test","question":"I hate this stupid system"}' \
  --data-urlencode "api_key=test-key"

# Test confidential data detection
curl -X POST "http://localhost:9090/api/v1/chat/query" \
  -d '{"knowledgeBaseId":"test","question":"My SSN is 123-45-6789"}' \
  --data-urlencode "api_key=test-key"
```

### Reranking Testing

```bash
# Test with reranking enabled
curl -X POST "http://localhost:9090/api/v1/chat/query" \
  -d '{"knowledgeBaseId":"test","question":"machine learning benefits"}' \
  --data-urlencode "api_key=test-key" \
  --data-urlencode "enable_reranking=true"

# Test without reranking
curl -X POST "http://localhost:9090/api/v1/chat/query" \
  -d '{"knowledgeBaseId":"test","question":"machine learning benefits"}' \
  --data-urlencode "api_key=test-key" \
  --data-urlencode "enable_reranking=false"
```

### Log Verification

```bash
# Check structured logs
tail -f logs/application.log | jq '.eventType'

# Monitor metrics
grep "METRIC_" logs/application.log | tail -10

# Check guardrails
grep "GUARDRAILS_METRIC" logs/application.log | jq '.valid'
```

## 🚀 Deployment Considerations

### Production Readiness

1. **Environment Variables**:
   ```bash
   export OPENAI_API_KEY=your-key
   export LOG_LEVEL=INFO
   export GUARDRAILS_STRICT_MODE=true
   ```

2. **CloudWatch Agent Configuration**:
   ```json
   {
     "logs": {
       "logs_collected": {
         "files": {
           "collect_list": [
             {
               "file_path": "/var/log/chatbot/application.log",
               "log_group_name": "/aws/ec2/chatbot-api/application",
               "log_stream_name": "{instance_id}"
             }
           ]
         }
       }
     }
   }
   ```

3. **Performance Tuning**:
   - Enable connection pooling for OpenAI API
   - Configure appropriate timeouts
   - Implement circuit breakers for external calls

### Security Considerations

1. **API Key Management**: Use AWS Secrets Manager
2. **Content Sanitization**: Log sanitized versions only
3. **Access Controls**: Implement proper RBAC
4. **Audit Trails**: Maintain comprehensive audit logs

## 🔮 Future Enhancements

### Reranking Improvements
1. **Batch Scoring**: Process multiple chunks in single LLM call
2. **Model Selection**: Support different models for scoring
3. **Caching**: Cache relevance scores for repeated queries
4. **A/B Testing**: Compare reranking strategies

### Guardrails Enhancements
1. **ML-Based Detection**: Train custom models for domain-specific content
2. **Real-time Updates**: Dynamic policy updates without restart
3. **User Feedback**: Learn from user corrections
4. **Integration**: Connect with external security services

### Observability Extensions
1. **Real-time Dashboards**: Live monitoring interfaces
2. **Predictive Alerts**: ML-based anomaly detection
3. **Cost Optimization**: Automatic scaling based on usage
4. **Compliance Reporting**: Automated compliance reports

This implementation provides a solid foundation for production-ready LLM reranking and content guardrails with comprehensive observability, ready for CloudWatch integration and enterprise deployment.
