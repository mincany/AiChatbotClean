#!/bin/bash

# AWS Resources Setup Script
# This script creates all required AWS resources for the chatbot system

set -e

echo "🚀 Setting up AWS resources for Chatbot System..."

# Configuration
REGION="us-east-1"
BUCKET_NAME="chatbot-knowledge-files-$(date +%s)"  # Make it unique
QUEUE_NAME="knowledge-processing-queue"
USERS_TABLE="chatbot-users"
KNOWLEDGE_TABLE="chatbot-knowledge"

# Check if AWS CLI is configured
if ! aws sts get-caller-identity > /dev/null 2>&1; then
    echo "❌ AWS CLI is not configured or credentials are invalid"
    echo "   Please run: aws configure"
    exit 1
fi

echo "✅ AWS CLI is configured"
echo "📍 Using region: $REGION"
echo "🪣 S3 bucket will be: $BUCKET_NAME"

# Create DynamoDB Tables
echo ""
echo "📊 Creating DynamoDB tables..."

# Users table
echo "   Creating users table: $USERS_TABLE"
aws dynamodb create-table \
    --table-name "$USERS_TABLE" \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$REGION" > /dev/null

# Knowledge base table
echo "   Creating knowledge table: $KNOWLEDGE_TABLE"
aws dynamodb create-table \
    --table-name "$KNOWLEDGE_TABLE" \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$REGION" > /dev/null

echo "✅ DynamoDB tables created successfully"

# Create S3 Bucket
echo ""
echo "🪣 Creating S3 bucket..."
aws s3 mb "s3://$BUCKET_NAME" --region "$REGION"

# Enable versioning
echo "   Enabling versioning..."
aws s3api put-bucket-versioning \
    --bucket "$BUCKET_NAME" \
    --versioning-configuration Status=Enabled

echo "✅ S3 bucket created: $BUCKET_NAME"

# Create SQS Queue
echo ""
echo "📬 Creating SQS queue..."
QUEUE_URL=$(aws sqs create-queue \
    --queue-name "$QUEUE_NAME" \
    --region "$REGION" \
    --attributes '{
        "VisibilityTimeoutSeconds": "300",
        "MessageRetentionPeriod": "1209600",
        "ReceiveMessageWaitTimeSeconds": "20"
    }' \
    --output text --query 'QueueUrl')

echo "✅ SQS queue created: $QUEUE_NAME"
echo "   Queue URL: $QUEUE_URL"

# Wait for resources to be ready
echo ""
echo "⏳ Waiting for resources to be fully available..."
sleep 10

# Verify resources
echo ""
echo "🔍 Verifying resources..."

# Check DynamoDB tables
echo "📊 DynamoDB tables:"
aws dynamodb list-tables --region "$REGION" --output table

# Check S3 bucket
echo ""
echo "🪣 S3 buckets:"
aws s3 ls | grep "$BUCKET_NAME"

# Check SQS queue
echo ""
echo "📬 SQS queues:"
aws sqs list-queues --region "$REGION" --output table

# Generate environment variables
echo ""
echo "📝 Environment Variables for your application:"
echo "=============================================="
echo ""
echo "# AWS Configuration"
echo "export AWS_REGION=\"$REGION\""
echo "export AWS_S3_BUCKET_NAME=\"$BUCKET_NAME\""
echo "export AWS_SQS_KNOWLEDGE_QUEUE=\"$QUEUE_NAME\""
echo "export AWS_DYNAMODB_USERS_TABLE=\"$USERS_TABLE\""
echo "export AWS_DYNAMODB_KNOWLEDGE_TABLE=\"$KNOWLEDGE_TABLE\""
echo ""
echo "# You still need to set these:"
echo "export AWS_ACCESS_KEY_ID=\"your-access-key-id\""
echo "export AWS_SECRET_ACCESS_KEY=\"your-secret-access-key\""
echo "export OPENAI_API_KEY=\"your-openai-api-key\""
echo "export PINECONE_API_KEY=\"your-pinecone-api-key\""
echo "export PINECONE_API_URL=\"your-pinecone-url\""
echo "export PINECONE_ENVIRONMENT=\"us-east-1-aws\""
echo "export PINECONE_PROJECT_ID=\"your-project-id\""

# Save to file
cat > aws-env-vars.sh << EOF
#!/bin/bash
# Generated AWS environment variables
# Add your API keys and run: source aws-env-vars.sh

# AWS Configuration
export AWS_REGION="$REGION"
export AWS_S3_BUCKET_NAME="$BUCKET_NAME"
export AWS_SQS_KNOWLEDGE_QUEUE="$QUEUE_NAME"
export AWS_DYNAMODB_USERS_TABLE="$USERS_TABLE"
export AWS_DYNAMODB_KNOWLEDGE_TABLE="$KNOWLEDGE_TABLE"

# TODO: Add your credentials
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"

# TODO: Add your API keys
export OPENAI_API_KEY="your-openai-api-key"
export PINECONE_API_KEY="your-pinecone-api-key"
export PINECONE_API_URL="your-pinecone-url"
export PINECONE_ENVIRONMENT="us-east-1-aws"
export PINECONE_PROJECT_ID="your-project-id"

echo "✅ Environment variables loaded!"
EOF

chmod +x aws-env-vars.sh

echo ""
echo "🎉 AWS resources setup completed successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Edit aws-env-vars.sh with your AWS credentials and API keys"
echo "2. Run: source aws-env-vars.sh"
echo "3. Run: mvn spring-boot:run"
echo "4. Test with: curl http://localhost:9090/api/v1/health"
echo ""
echo "📁 Files created:"
echo "   - aws-env-vars.sh (environment variables script)"
echo ""
echo "🔒 Security note:"
echo "   - Keep your AWS credentials secure"
echo "   - Consider using IAM roles instead of access keys for production"
echo "   - Add aws-env-vars.sh to .gitignore to avoid committing secrets"

# Add to gitignore
if [ -f .gitignore ]; then
    if ! grep -q "aws-env-vars.sh" .gitignore; then
        echo "aws-env-vars.sh" >> .gitignore
        echo "✅ Added aws-env-vars.sh to .gitignore"
    fi
else
    echo "aws-env-vars.sh" > .gitignore
    echo "✅ Created .gitignore with aws-env-vars.sh"
fi
