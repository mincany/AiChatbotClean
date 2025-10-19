# Complete Deployment & Setup Guide

This guide covers everything needed to get the chatbot system running in both local development and production environments.

## 🏗️ Infrastructure Requirements

### Required Services

1. **AWS DynamoDB** - User and knowledge base storage
2. **AWS S3** - File storage
3. **AWS SQS** - Message queuing
4. **OpenAI API** - Text processing and embeddings
5. **Pinecone** - Vector database for embeddings

## 🔧 Environment Setup

### 1. IAM Role and Security Setup

#### Create IAM Role for Application

For production deployments, create an IAM role instead of using hardcoded AWS credentials:

```bash
# 1. Create IAM role
aws iam create-role --role-name chatbot-application-role --assume-role-policy-document '{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}'

# 2. Create IAM policy
aws iam create-policy --policy-name chatbot-application-policy --policy-document '{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "S3KnowledgeFilesAccess",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::chatbot-knowledge-files",
        "arn:aws:s3:::chatbot-knowledge-files/*"
      ]
    },
    {
      "Sid": "SQSKnowledgeProcessingAccess",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ],
      "Resource": "arn:aws:sqs:us-east-1:ACCOUNT_ID:knowledge-processing-queue"
    },
    {
      "Sid": "DynamoDBTablesAccess",
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query",
        "dynamodb:Scan"
      ],
      "Resource": [
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-users",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-users/index/*",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-knowledge",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-knowledge/index/*",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-conversations",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-conversations/index/*",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-messages",
        "arn:aws:dynamodb:us-east-1:ACCOUNT_ID:table/chatbot-messages/index/*"
      ]
    },
    {
      "Sid": "CloudWatchLogsAccess",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams",
        "logs:DescribeLogGroups"
      ],
      "Resource": "arn:aws:logs:us-east-1:ACCOUNT_ID:log-group:/aws/chatbot/*"
    }
  ]
}'

# 3. Attach policy to role
aws iam attach-role-policy --role-name chatbot-application-role --policy-arn arn:aws:iam::ACCOUNT_ID:policy/chatbot-application-policy

# 4. Create instance profile for EC2
aws iam create-instance-profile --instance-profile-name chatbot-application-instance-profile

# 5. Add role to instance profile
aws iam add-role-to-instance-profile --instance-profile-name chatbot-application-instance-profile --role-name chatbot-application-role
```

> **Note**: Replace `ACCOUNT_ID` with your actual AWS account ID.

#### Role ARNs (Created):
- **Role ARN**: `arn:aws:iam::277707115624:role/chatbot-application-role`
- **Policy ARN**: `arn:aws:iam::277707115624:policy/chatbot-application-policy`
- **Instance Profile ARN**: `arn:aws:iam::277707115624:instance-profile/chatbot-application-instance-profile`

#### Using the IAM Role

**For EC2 Deployment:**
1. Launch EC2 instance with the `chatbot-application-instance-profile`
2. Remove hardcoded AWS credentials from `application.properties`
3. Application will automatically use IAM role credentials

**For Local Development:**
- Continue using AWS credentials in `application.properties` or AWS CLI profile
- Or use AWS CLI to assume the role temporarily

### 2. AWS Services Setup

#### DynamoDB Tables
Create four tables in AWS DynamoDB with the following structures:

> **Quick Setup**: Use the provided script `./setup-dynamodb-tables.sh` to create all tables at once:
> ```bash
> # For AWS DynamoDB
> ./setup-dynamodb-tables.sh
> 
> # For LocalStack
> AWS_DYNAMODB_ENDPOINT=http://localhost:4566 ./setup-dynamodb-tables.sh
> ```

##### 1. User Table
| Attribute | Data Type | Description |
|-----------|-----------|-------------|
| UserId | String | UUID for user (Primary Key) |
| SubscriptionType | String | User subscription level |
| APIKey | String | User's API key (GSI) |
| Name | String | User's display name |
| Email | String | User's email address |
| CreatedAt | Timestamp | Account creation timestamp |
| PasswordHash | String | Hashed password |

```bash
# Create Users table
aws dynamodb create-table \
    --table-name chatbot-users \
    --attribute-definitions \
        AttributeName=UserId,AttributeType=S \
        AttributeName=APIKey,AttributeType=S \
    --key-schema \
        AttributeName=UserId,KeyType=HASH \
    --global-secondary-indexes \
        IndexName=APIKey-index,KeySchema=[{AttributeName=APIKey,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1
```

##### 2. AIKnowledge Table
| Attribute | Data Type | Description |
|-----------|-----------|-------------|
| KnowledgeId | String | UUID for knowledge base (Primary Key) |
| UserId | String | Owner user ID (GSI) |
| Data | String | S3 URL to file or link |
| CreatedAt | Timestamp | Creation timestamp |
| UpdatedAt | Timestamp | Last update timestamp |

```bash
# Create AIKnowledge table
aws dynamodb create-table \
    --table-name chatbot-knowledge \
    --attribute-definitions \
        AttributeName=KnowledgeId,AttributeType=S \
        AttributeName=UserId,AttributeType=S \
    --key-schema \
        AttributeName=KnowledgeId,KeyType=HASH \
    --global-secondary-indexes \
        IndexName=UserId-index,KeySchema=[{AttributeName=UserId,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1
```

##### 3. Conversation Table
| Attribute | Data Type | Description |
|-----------|-----------|-------------|
| ConversationId | String | UUID for conversation (Primary Key) |
| UserId | String | User who owns the conversation |
| LastMessageId | String | UUID of last sent message |
| LastMessageTime | Timestamp | Timestamp for when the message was sent (Sort Key) |
| Status | String | Enum (open, solved, pending) |
| CreatedAt | Timestamp | Conversation creation timestamp |
| UpdatedAt | Timestamp | Last update timestamp |

```bash
# Create Conversation table
aws dynamodb create-table \
    --table-name chatbot-conversations \
    --attribute-definitions \
        AttributeName=ConversationId,AttributeType=S \
        AttributeName=LastMessageTime,AttributeType=S \
        AttributeName=UserId,AttributeType=S \
    --key-schema \
        AttributeName=ConversationId,KeyType=HASH \
        AttributeName=LastMessageTime,KeyType=RANGE \
    --global-secondary-indexes \
        IndexName=UserId-LastMessageTime-index,KeySchema=[{AttributeName=UserId,KeyType=HASH},{AttributeName=LastMessageTime,KeyType=RANGE}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1
```

##### 4. Message Table
| Attribute | Data Type | Description |
|-----------|-----------|-------------|
| ConversationId | String | UUID for conversation (Primary Key) |
| CreatedTime | Timestamp | Time created (Sort Key) |
| MessageId | String | UUID for message |
| MessageContent | String | The message content |
| SenderType | String | Enum (team, ai, customer) |
| Status | String | Enum (open, solved, pending) |
| Metadata | String | Additional message metadata |

```bash
# Create Message table
aws dynamodb create-table \
    --table-name chatbot-messages \
    --attribute-definitions \
        AttributeName=ConversationId,AttributeType=S \
        AttributeName=CreatedTime,AttributeType=S \
        AttributeName=MessageId,AttributeType=S \
    --key-schema \
        AttributeName=ConversationId,KeyType=HASH \
        AttributeName=CreatedTime,KeyType=RANGE \
    --global-secondary-indexes \
        IndexName=MessageId-index,KeySchema=[{AttributeName=MessageId,KeyType=HASH}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1
```

#### Table Relationships and Access Patterns

##### Access Patterns:
1. **User Management**:
   - Find user by UserId (PK)
   - Find user by APIKey (GSI: APIKey-index)

2. **Knowledge Base Management**:
   - Find knowledge base by KnowledgeId (PK)
   - Find all knowledge bases for a user (GSI: UserId-index)

3. **Conversation Management**:
   - Find conversation by ConversationId and LastMessageTime (PK + SK)
   - Find all conversations for a user ordered by LastMessageTime (GSI: UserId-LastMessageTime-index)

4. **Message Management**:
   - Find all messages in a conversation ordered by CreatedTime (PK + SK)
   - Find specific message by MessageId (GSI: MessageId-index)

##### Data Flow:
```
User → APIKey (authentication) → UserId
UserId → Knowledge Bases (via GSI)
UserId → Conversations (via GSI)
ConversationId → Messages (via PK)
```

#### S3 Bucket
```bash
# Create S3 bucket for file storage
aws s3 mb s3://chatbot-knowledge-files --region us-east-1
```

#### SQS Queue
```bash
# Create SQS queue for knowledge processing
aws sqs create-queue \
    --queue-name knowledge-processing-queue \
    --region us-east-1
```

### 2. External Services Setup

#### OpenAI API
1. Sign up at https://platform.openai.com/
2. Generate API key
3. Set up billing (required for API access)

#### Pinecone Setup
1. Sign up at https://www.pinecone.io/
2. Create a new project
3. Create an index:
   - Name: `chatbot-poc`
   - Dimensions: `1536` (for OpenAI embeddings)
   - Metric: `cosine`
   - Environment: Choose your preferred region

## 🚀 Application Startup

### 1. Build the Application
```bash
mvn clean package -DskipTests
```

### 2. Run the Application
```bash
# Using Maven
mvn spring-boot:run

# Or using Java directly
java -jar target/chatbot-0.0.1-SNAPSHOT.jar
```

### 3. Verify Application is Running
```bash
# Health check
curl http://localhost:9090/api/v1/health

# Expected response:
{
  "status": "UP",
  "timestamp": "2024-01-01T12:00:00Z",
  "version": "0.0.1-SNAPSHOT",
  "services": {
    "database": "UP",
    "openai": "UP",
    "pinecone": "UP"
  }
}
```

## 🧪 Testing the System

### 1. Create a Test User
```bash
curl -X POST "http://localhost:9090/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'
```

### 2. Upload a Knowledge Base File
```bash
# Create a test file
echo "This is a test document for the knowledge base. It contains important information about our products and services." > test-document.txt

# Upload the file
curl -X POST "http://localhost:9090/api/v1/knowledge/import" \
  -H "Content-Type: application/json" \
  -d '{
    "file": "/path/to/test-document.txt",
    "name": "Test Document",
    "description": "A test document for verification"
  }' \
  -G -d "api_key=your-api-key-from-registration"
```

### 3. Check Processing Status
```bash
curl -X GET "http://localhost:9090/api/v1/knowledge/{knowledge_base_id}/status" \
  -G -d "api_key=your-api-key"
```

### 4. Test Chat with Knowledge Base
```bash
curl -X POST "http://localhost:9090/api/v1/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What information do you have about our products?",
    "conversation_id": "test-conversation"
  }' \
  -G -d "api_key=your-api-key"
```

## 🔍 Troubleshooting

### Common Issues

#### 1. Application Won't Start
- Check all environment variables are set
- Verify AWS credentials are valid
- Ensure all required services are running

#### 2. SQS Messages Not Processing
- Check SQS queue exists and is accessible
- Verify AWS credentials have SQS permissions
- Check application logs for SQS listener errors

#### 3. S3 Upload Failures
- Verify S3 bucket exists and is accessible
- Check AWS credentials have S3 permissions
- Ensure bucket name is unique globally

#### 4. Pinecone Connection Issues
- Verify Pinecone API key is correct
- Check Pinecone index exists with correct dimensions
- Ensure Pinecone URL matches your index

### Debugging Commands
