package com.example.chatbot.config;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.KnowledgeBase;
import com.example.chatbot.model.Message;
import com.example.chatbot.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${aws.dynamodb.users-table}")
    private String usersTableName;

    @Value("${aws.dynamodb.knowledge-table}")
    private String knowledgeTableName;

    @Value("${aws.dynamodb.conversations-table}")
    private String conversationsTableName;

    @Value("${aws.dynamodb.messages-table}")
    private String messagesTableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                // Uses AWS Default Credential Chain:
                // 1. IAM role (EC2 instance profile) - for production
                // 2. Environment variables - for local dev
                // 3. AWS credentials file - for local dev
                // 4. Other credential sources
                .credentialsProvider(DefaultCredentialsProvider.create());

        // For local development with LocalStack
        if (dynamoDbEndpoint != null && !dynamoDbEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(dynamoDbEndpoint));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<User> userTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(usersTableName, TableSchema.fromBean(User.class));
    }

    @Bean
    public DynamoDbTable<KnowledgeBase> knowledgeBaseTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(knowledgeTableName, TableSchema.fromBean(KnowledgeBase.class));
    }

    @Bean
    public DynamoDbTable<Conversation> conversationTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(conversationsTableName, TableSchema.fromBean(Conversation.class));
    }

    @Bean
    public DynamoDbTable<Message> messageTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(messagesTableName, TableSchema.fromBean(Message.class));
    }

    // Getter methods for table names (useful for repositories)
    public String getUsersTableName() {
        return usersTableName;
    }

    public String getKnowledgeTableName() {
        return knowledgeTableName;
    }

    public String getConversationsTableName() {
        return conversationsTableName;
    }

    public String getMessagesTableName() {
        return messagesTableName;
    }
} 