package com.example.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class SqsConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.sqs.endpoint:}")
    private String sqsEndpoint;

    @Value("${aws.access-key-id:}")
    private String accessKey;

    @Value("${aws.secret-access-key:}")
    private String secretKey;

    @Bean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(awsCredentialsProvider());

        // Use custom endpoint if provided (for LocalStack or custom SQS endpoint)
        if (sqsEndpoint != null && !sqsEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(awsCredentialsProvider());

        // Use custom endpoint if provided (for LocalStack or custom SQS endpoint)
        if (sqsEndpoint != null && !sqsEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }

    private AwsCredentialsProvider awsCredentialsProvider() {
        // Use provided credentials if available, otherwise use default provider chain
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
