package com.example.chatbot.repository;

import com.example.chatbot.model.Conversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ConversationRepository {

    private final DynamoDbTable<Conversation> conversationTable;
    private final DynamoDbIndex<Conversation> userIdIndex;

    @Autowired
    public ConversationRepository(DynamoDbTable<Conversation> conversationTable) {
        this.conversationTable = conversationTable;
        this.userIdIndex = conversationTable.index("UserId-LastMessageTime-index");
    }

    /**
     * Save a conversation
     */
    public Conversation save(Conversation conversation) {
        conversationTable.putItem(conversation);
        return conversation;
    }

    /**
     * Find conversation by ID and last message time
     */
    public Optional<Conversation> findById(String conversationId, String lastMessageTime) {
        Key key = Key.builder()
                .partitionValue(conversationId)
                .sortValue(lastMessageTime)
                .build();
        
        Conversation conversation = conversationTable.getItem(key);
        return Optional.ofNullable(conversation);
    }

    /**
     * Find the latest conversation by ID (most recent lastMessageTime)
     */
    public Optional<Conversation> findLatestById(String conversationId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(conversationId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Descending order (latest first)
                .limit(1)
                .build();

        Page<Conversation> page = conversationTable.query(request).stream().findFirst().orElse(null);
        
        if (page != null && !page.items().isEmpty()) {
            return Optional.of(page.items().get(0));
        }
        
        return Optional.empty();
    }

    /**
     * Find all conversations for a user, ordered by last message time (latest first)
     */
    public List<Conversation> findByUserId(String userId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Descending order (latest first)
                .build();

        return userIdIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Find conversations for a user with pagination
     */
    public List<Conversation> findByUserId(String userId, int limit) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Descending order (latest first)
                .limit(limit)
                .build();

        return userIdIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    /**
     * Update conversation status
     */
    public void updateStatus(String conversationId, String lastMessageTime, String status) {
        Optional<Conversation> conversationOpt = findById(conversationId, lastMessageTime);
        if (conversationOpt.isPresent()) {
            Conversation conversation = conversationOpt.get();
            conversation.setStatus(status);
            save(conversation);
        }
    }

    /**
     * Delete a conversation
     */
    public void delete(String conversationId, String lastMessageTime) {
        Key key = Key.builder()
                .partitionValue(conversationId)
                .sortValue(lastMessageTime)
                .build();
        
        conversationTable.deleteItem(key);
    }

    /**
     * Check if conversation exists
     */
    public boolean existsById(String conversationId, String lastMessageTime) {
        return findById(conversationId, lastMessageTime).isPresent();
    }
}
