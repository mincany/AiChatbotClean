package com.example.chatbot.service;

import com.example.chatbot.dto.KnowledgeProcessingMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;

@Service
public class SqsService {

    private static final Logger logger = LoggerFactory.getLogger(SqsService.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String knowledgeProcessingQueueUrl;

    @Autowired
    public SqsService(SqsClient sqsClient, 
                     ObjectMapper objectMapper,
                     @Value("${aws.sqs.knowledge-processing-queue}") String queueName) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.knowledgeProcessingQueueUrl = getOrCreateQueueUrl(queueName);
    }

    /**
     * Send knowledge processing message to SQS queue
     * 
     * @param message The knowledge processing message
     * @return Message ID from SQS
     */
    public String sendKnowledgeProcessingMessage(KnowledgeProcessingMessage message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            
            // Add message attributes for better filtering and monitoring
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("MessageType", MessageAttributeValue.builder()
                    .stringValue("KnowledgeProcessing")
                    .dataType("String")
                    .build());
            messageAttributes.put("UserId", MessageAttributeValue.builder()
                    .stringValue(message.getUserId())
                    .dataType("String")
                    .build());
            messageAttributes.put("KnowledgeBaseId", MessageAttributeValue.builder()
                    .stringValue(message.getKnowledgeBaseId())
                    .dataType("String")
                    .build());

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(knowledgeProcessingQueueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(messageAttributes)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);
            
            logger.info("Successfully sent knowledge processing message to SQS: messageId={}, knowledgeBaseId={}", 
                    response.messageId(), message.getKnowledgeBaseId());
            
            return response.messageId();

        } catch (JsonProcessingException e) {
            logger.error("Error serializing knowledge processing message: {}", message, e);
            throw new RuntimeException("Failed to serialize message", e);
        } catch (Exception e) {
            logger.error("Error sending knowledge processing message to SQS: {}", message, e);
            throw new RuntimeException("Failed to send message to SQS", e);
        }
    }

    /**
     * Send message with retry logic for failed processing
     * 
     * @param message The knowledge processing message
     * @param delaySeconds Delay before message becomes available
     * @return Message ID from SQS
     */
    public String sendKnowledgeProcessingMessageWithDelay(KnowledgeProcessingMessage message, int delaySeconds) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("MessageType", MessageAttributeValue.builder()
                    .stringValue("KnowledgeProcessingRetry")
                    .dataType("String")
                    .build());
            messageAttributes.put("UserId", MessageAttributeValue.builder()
                    .stringValue(message.getUserId())
                    .dataType("String")
                    .build());
            messageAttributes.put("KnowledgeBaseId", MessageAttributeValue.builder()
                    .stringValue(message.getKnowledgeBaseId())
                    .dataType("String")
                    .build());
            messageAttributes.put("RetryCount", MessageAttributeValue.builder()
                    .stringValue(String.valueOf(message.getRetryCount()))
                    .dataType("Number")
                    .build());

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(knowledgeProcessingQueueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(messageAttributes)
                    .delaySeconds(delaySeconds)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);
            
            logger.info("Successfully sent retry knowledge processing message to SQS: messageId={}, knowledgeBaseId={}, retryCount={}, delaySeconds={}", 
                    response.messageId(), message.getKnowledgeBaseId(), message.getRetryCount(), delaySeconds);
            
            return response.messageId();

        } catch (JsonProcessingException e) {
            logger.error("Error serializing retry knowledge processing message: {}", message, e);
            throw new RuntimeException("Failed to serialize retry message", e);
        } catch (Exception e) {
            logger.error("Error sending retry knowledge processing message to SQS: {}", message, e);
            throw new RuntimeException("Failed to send retry message to SQS", e);
        }
    }

    /**
     * Get or create SQS queue URL
     * 
     * @param queueName Queue name
     * @return Queue URL
     */
    private String getOrCreateQueueUrl(String queueName) {
        try {
            // Try to get existing queue URL
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();
            
            GetQueueUrlResponse response = sqsClient.getQueueUrl(getQueueUrlRequest);
            logger.info("Found existing SQS queue: {} -> {}", queueName, response.queueUrl());
            return response.queueUrl();

        } catch (QueueDoesNotExistException e) {
            // Queue doesn't exist, create it
            logger.info("Creating SQS queue: {}", queueName);
            return createQueue(queueName);
        } catch (Exception e) {
            logger.error("Error getting SQS queue URL: {}", queueName, e);
            throw new RuntimeException("Failed to get or create SQS queue", e);
        }
    }

    /**
     * Create SQS queue with appropriate configuration
     * 
     * @param queueName Queue name
     * @return Queue URL
     */
    private String createQueue(String queueName) {
        try {
            // Configure queue attributes
            Map<QueueAttributeName, String> queueAttributes = new HashMap<>();
            
            // Set visibility timeout (time message is invisible after being received)
            queueAttributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "300"); // 5 minutes
            
            // Set message retention period
            queueAttributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"); // 14 days
            
            // Set receive message wait time (long polling)
            queueAttributes.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20");
            
            // Set max receive count for dead letter queue
            queueAttributes.put(QueueAttributeName.REDRIVE_POLICY, 
                    "{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:123456789012:" + queueName + "-dlq\",\"maxReceiveCount\":3}");

            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .attributes(queueAttributes)
                    .build();

            CreateQueueResponse response = sqsClient.createQueue(createQueueRequest);
            
            logger.info("Successfully created SQS queue: {} -> {}", queueName, response.queueUrl());
            return response.queueUrl();

        } catch (Exception e) {
            logger.error("Error creating SQS queue: {}", queueName, e);
            throw new RuntimeException("Failed to create SQS queue", e);
        }
    }
}
