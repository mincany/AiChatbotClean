package com.example.chatbot.repository;

import com.example.chatbot.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MessageRepository {

    private final DynamoDbTable<Message> messageTable;
    private final DynamoDbIndex<Message> messageIdIndex;

    @Autowired
    public MessageRepository(DynamoDbTable<Message> messageTable) {
        this.messageTable = messageTable;
        this.messageIdIndex = messageTable.index("MessageId-index");
    }

    /**
     * Save a message
     */
    public Message save(Message message) {
        messageTable.putItem(message);
        return message;
    }

    /**
     * Find message by conversation ID and created time
     */
    public Optional<Message> findById(String conversationId, String createdTime) {
        Key key = Key.builder()
                .partitionValue(conversationId)
                .sortValue(createdTime)
                .build();
        
        Message message = messageTable.getItem(key);
        return Optional.ofNullable(message);
    }

    /**
     * Find message by message ID using GSI
     */
    public Optional<Message> findByMessageId(String messageId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(messageId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(1)
                .build();

        return messageIdIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    /**
     * Find all messages in a conversation, ordered by created time (oldest first)
     */
    public List<Message> findByConversationId(String conversationId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(conversationId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true) // Ascending order (oldest first)
                .build();

        return messageTable.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Find messages in a conversation with pagination
     */
    public List<Message> findByConversationId(String conversationId, int limit) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(conversationId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true) // Ascending order (oldest first)
                .limit(limit)
                .build();

        return messageTable.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Find recent messages in a conversation (latest first)
     */
    public List<Message> findRecentByConversationId(String conversationId, int limit) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(conversationId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Descending order (latest first)
                .limit(limit)
                .build();

        return messageTable.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Find messages by sender type in a conversation
     */
    public List<Message> findByConversationIdAndSenderType(String conversationId, String senderType) {
        return findByConversationId(conversationId)
                .stream()
                .filter(message -> senderType.equals(message.getSenderType()))
                .collect(Collectors.toList());
    }

    /**
     * Update message status
     */
    public void updateStatus(String conversationId, String createdTime, String status) {
        Optional<Message> messageOpt = findById(conversationId, createdTime);
        if (messageOpt.isPresent()) {
            Message message = messageOpt.get();
            message.setStatus(status);
            save(message);
        }
    }

    /**
     * Delete a message
     */
    public void delete(String conversationId, String createdTime) {
        Key key = Key.builder()
                .partitionValue(conversationId)
                .sortValue(createdTime)
                .build();
        
        messageTable.deleteItem(key);
    }

    /**
     * Delete all messages in a conversation
     */
    public void deleteByConversationId(String conversationId) {
        List<Message> messages = findByConversationId(conversationId);
        for (Message message : messages) {
            delete(message.getConversationId(), message.getCreatedTime());
        }
    }

    /**
     * Check if message exists
     */
    public boolean existsById(String conversationId, String createdTime) {
        return findById(conversationId, createdTime).isPresent();
    }

    /**
     * Count messages in a conversation
     */
    public long countByConversationId(String conversationId) {
        return findByConversationId(conversationId).size();
    }
}
