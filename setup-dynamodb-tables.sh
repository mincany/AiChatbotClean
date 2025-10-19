#!/bin/bash

# DynamoDB Tables Setup Script for Chatbot Application
# This script creates all required DynamoDB tables with proper indexes

set -e

# Configuration
REGION=${AWS_REGION:-us-east-1}
ENDPOINT_URL=${AWS_DYNAMODB_ENDPOINT:-""}

# Set endpoint URL for LocalStack if provided
if [ -n "$ENDPOINT_URL" ]; then
    ENDPOINT_PARAM="--endpoint-url $ENDPOINT_URL"
    echo "🔧 Using custom DynamoDB endpoint: $ENDPOINT_URL"
else
    ENDPOINT_PARAM=""
    echo "🌐 Using AWS DynamoDB in region: $REGION"
fi

echo "🚀 Creating DynamoDB tables for Chatbot application..."

# Function to create table with retry logic
create_table() {
    local table_name=$1
    local create_command=$2
    
    echo "📋 Creating table: $table_name"
    
    if eval "$create_command"; then
        echo "✅ Successfully created table: $table_name"
    else
        echo "❌ Failed to create table: $table_name"
        return 1
    fi
    
    # Wait for table to be active (only for real AWS, not LocalStack)
    if [ -z "$ENDPOINT_URL" ]; then
        echo "⏳ Waiting for table $table_name to become active..."
        aws dynamodb wait table-exists --table-name "$table_name" --region "$REGION"
        echo "✅ Table $table_name is now active"
    fi
}

# 1. Create Users Table
echo ""
echo "1️⃣ Creating Users Table (chatbot-users)"
echo "   - Primary Key: UserId"
echo "   - GSI: APIKey-index"

create_table "chatbot-users" "aws dynamodb create-table $ENDPOINT_PARAM \
    --table-name chatbot-users \
    --attribute-definitions \
        AttributeName=UserId,AttributeType=S \
        AttributeName=APIKey,AttributeType=S \
    --key-schema \
        AttributeName=UserId,KeyType=HASH \
    --global-secondary-indexes \
        'IndexName=APIKey-index,KeySchema=[{AttributeName=APIKey,KeyType=HASH}],Projection={ProjectionType=ALL}' \
    --billing-mode PAY_PER_REQUEST \
    --region $REGION"

# 2. Create AIKnowledge Table
echo ""
echo "2️⃣ Creating AIKnowledge Table (chatbot-knowledge)"
echo "   - Primary Key: KnowledgeId"
echo "   - GSI: UserId-index"

create_table "chatbot-knowledge" "aws dynamodb create-table $ENDPOINT_PARAM \
    --table-name chatbot-knowledge \
    --attribute-definitions \
        AttributeName=KnowledgeId,AttributeType=S \
        AttributeName=UserId,AttributeType=S \
    --key-schema \
        AttributeName=KnowledgeId,KeyType=HASH \
    --global-secondary-indexes \
        'IndexName=UserId-index,KeySchema=[{AttributeName=UserId,KeyType=HASH}],Projection={ProjectionType=ALL}' \
    --billing-mode PAY_PER_REQUEST \
    --region $REGION"

# 3. Create Conversations Table
echo ""
echo "3️⃣ Creating Conversations Table (chatbot-conversations)"
echo "   - Primary Key: ConversationId"
echo "   - Sort Key: LastMessageTime"
echo "   - GSI: UserId-LastMessageTime-index"

create_table "chatbot-conversations" "aws dynamodb create-table $ENDPOINT_PARAM \
    --table-name chatbot-conversations \
    --attribute-definitions \
        AttributeName=ConversationId,AttributeType=S \
        AttributeName=LastMessageTime,AttributeType=S \
        AttributeName=UserId,AttributeType=S \
    --key-schema \
        AttributeName=ConversationId,KeyType=HASH \
        AttributeName=LastMessageTime,KeyType=RANGE \
    --global-secondary-indexes \
        'IndexName=UserId-LastMessageTime-index,KeySchema=[{AttributeName=UserId,KeyType=HASH},{AttributeName=LastMessageTime,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
    --billing-mode PAY_PER_REQUEST \
    --region $REGION"

# 4. Create Messages Table
echo ""
echo "4️⃣ Creating Messages Table (chatbot-messages)"
echo "   - Primary Key: ConversationId"
echo "   - Sort Key: CreatedTime"
echo "   - GSI: MessageId-index"

create_table "chatbot-messages" "aws dynamodb create-table $ENDPOINT_PARAM \
    --table-name chatbot-messages \
    --attribute-definitions \
        AttributeName=ConversationId,AttributeType=S \
        AttributeName=CreatedTime,AttributeType=S \
        AttributeName=MessageId,AttributeType=S \
    --key-schema \
        AttributeName=ConversationId,KeyType=HASH \
        AttributeName=CreatedTime,KeyType=RANGE \
    --global-secondary-indexes \
        'IndexName=MessageId-index,KeySchema=[{AttributeName=MessageId,KeyType=HASH}],Projection={ProjectionType=ALL}' \
    --billing-mode PAY_PER_REQUEST \
    --region $REGION"

echo ""
echo "🎉 All DynamoDB tables created successfully!"
echo ""
echo "📊 Table Summary:"
echo "   ✅ chatbot-users (UserId PK, APIKey GSI)"
echo "   ✅ chatbot-knowledge (KnowledgeId PK, UserId GSI)"
echo "   ✅ chatbot-conversations (ConversationId PK, LastMessageTime SK, UserId GSI)"
echo "   ✅ chatbot-messages (ConversationId PK, CreatedTime SK, MessageId GSI)"
echo ""
echo "🔍 Verify tables:"
if [ -n "$ENDPOINT_URL" ]; then
    echo "   aws dynamodb list-tables --endpoint-url $ENDPOINT_URL --region $REGION"
else
    echo "   aws dynamodb list-tables --region $REGION"
fi
echo ""
echo "📋 Next steps:"
echo "   1. Create S3 bucket: aws s3 mb s3://chatbot-knowledge-files --region $REGION"
echo "   2. Create SQS queue: aws sqs create-queue --queue-name knowledge-processing-queue --region $REGION"
echo "   3. Configure application.properties with table names"
echo "   4. Start the application and test endpoints"
