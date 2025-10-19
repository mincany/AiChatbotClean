package com.example.chatbot.repository;

import com.example.chatbot.model.KnowledgeBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class KnowledgeBaseRepository {

    private final DynamoDbTable<KnowledgeBase> knowledgeBaseTable;
    private final DynamoDbIndex<KnowledgeBase> userIdIndex;

    @Autowired
    public KnowledgeBaseRepository(DynamoDbTable<KnowledgeBase> knowledgeBaseTable) {
        this.knowledgeBaseTable = knowledgeBaseTable;
        this.userIdIndex = knowledgeBaseTable.index("UserId-index");
    }

    public void save(KnowledgeBase knowledgeBase) {
        knowledgeBaseTable.putItem(knowledgeBase);
    }

    public Optional<KnowledgeBase> findById(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseTable.getItem(Key.builder().partitionValue(id).build());
        return Optional.ofNullable(knowledgeBase);
    }

    public List<KnowledgeBase> findByUserId(String userId) {
        // Use GSI to query by userId
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());

        return userIdIndex.query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        knowledgeBaseTable.deleteItem(Key.builder().partitionValue(id).build());
    }
} 