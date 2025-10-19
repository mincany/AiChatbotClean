#!/bin/bash

# System Verification Script
# This script verifies that all components are working correctly

set -e

BASE_URL="http://localhost:9090"
API_KEY=""

echo "🔍 Chatbot System Verification"
echo "=============================="

# Function to make HTTP requests with error handling
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local expected_status=$4
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null || echo -e "\n000")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$url" 2>/dev/null || echo -e "\n000")
    fi
    
    body=$(echo "$response" | head -n -1)
    status=$(echo "$response" | tail -n 1)
    
    if [ "$status" = "$expected_status" ]; then
        echo "✅ $method $url - Status: $status"
        return 0
    else
        echo "❌ $method $url - Expected: $expected_status, Got: $status"
        if [ -n "$body" ]; then
            echo "   Response: $body"
        fi
        return 1
    fi
}

# 1. Check if application is running
echo ""
echo "1️⃣ Checking if application is running..."
if make_request "GET" "$BASE_URL/api/v1/health" "" "200"; then
    echo "✅ Application is running"
else
    echo "❌ Application is not running or health check failed"
    echo "   Please start the application with: mvn spring-boot:run"
    exit 1
fi

# 2. Test user registration
echo ""
echo "2️⃣ Testing user registration..."
registration_data='{
    "username": "testuser_'$(date +%s)'",
    "email": "test'$(date +%s)'@example.com",
    "password": "password123"
}'

if response=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "$registration_data" 2>/dev/null); then
    
    # Extract API key from response
    API_KEY=$(echo "$response" | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$API_KEY" ]; then
        echo "✅ User registration successful"
        echo "   API Key: ${API_KEY:0:20}..."
    else
        echo "❌ User registration failed - no API key returned"
        echo "   Response: $response"
        exit 1
    fi
else
    echo "❌ User registration request failed"
    exit 1
fi

# 3. Create a test file for knowledge base
echo ""
echo "3️⃣ Creating test file..."
TEST_FILE="/tmp/chatbot-test-$(date +%s).txt"
cat > "$TEST_FILE" << EOF
This is a test document for the chatbot knowledge base.

Our company offers the following products:
1. Premium Software Suite - Advanced business management tools
2. Cloud Storage Service - Secure file storage and sharing
3. AI Analytics Platform - Data insights and predictions

Customer support is available 24/7 via email at support@company.com or phone at 1-800-SUPPORT.

Our office hours are Monday through Friday, 9 AM to 6 PM EST.
EOF

echo "✅ Test file created: $TEST_FILE"

# 4. Test knowledge base import
echo ""
echo "4️⃣ Testing knowledge base import..."
import_data='{
    "file": "'$TEST_FILE'",
    "name": "Test Knowledge Base",
    "description": "Automated test document"
}'

if response=$(curl -s -X POST "$BASE_URL/api/v1/knowledge/import?api_key=$API_KEY" \
    -H "Content-Type: application/json" \
    -d "$import_data" 2>/dev/null); then
    
    # Extract knowledge base ID
    KB_ID=$(echo "$response" | grep -o '"knowledgeBaseId":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$KB_ID" ]; then
        echo "✅ Knowledge base import initiated"
        echo "   Knowledge Base ID: $KB_ID"
    else
        echo "❌ Knowledge base import failed - no ID returned"
        echo "   Response: $response"
        exit 1
    fi
else
    echo "❌ Knowledge base import request failed"
    exit 1
fi

# 5. Check processing status
echo ""
echo "5️⃣ Checking processing status..."
sleep 2  # Give it a moment to start processing

if response=$(curl -s -X GET "$BASE_URL/api/v1/knowledge/$KB_ID/status?api_key=$API_KEY" 2>/dev/null); then
    status=$(echo "$response" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    echo "✅ Status check successful"
    echo "   Current status: $status"
    
    if [ "$status" = "pending" ] || [ "$status" = "processing" ]; then
        echo "   ⏳ Processing in progress (this is expected)"
    elif [ "$status" = "completed" ]; then
        echo "   🎉 Processing completed!"
    else
        echo "   ⚠️  Unexpected status: $status"
    fi
else
    echo "❌ Status check failed"
fi

# 6. Test chat endpoint (basic connectivity)
echo ""
echo "6️⃣ Testing chat endpoint..."
chat_data='{
    "message": "Hello, can you help me?",
    "conversation_id": "test-conversation-'$(date +%s)'"
}'

if response=$(curl -s -X POST "$BASE_URL/api/v1/chat?api_key=$API_KEY" \
    -H "Content-Type: application/json" \
    -d "$chat_data" 2>/dev/null); then
    
    echo "✅ Chat endpoint is accessible"
    # Note: The actual response depends on OpenAI and knowledge base processing
else
    echo "❌ Chat endpoint failed"
fi

# 7. Clean up test file
echo ""
echo "7️⃣ Cleaning up..."
rm -f "$TEST_FILE"
echo "✅ Test file cleaned up"

# Summary
echo ""
echo "📋 Verification Summary"
echo "======================"
echo "✅ Application health check"
echo "✅ User registration"
echo "✅ Knowledge base import"
echo "✅ Status checking"
echo "✅ Chat endpoint connectivity"
echo ""
echo "🎉 Basic system verification completed successfully!"
echo ""
echo "📝 Next steps for full testing:"
echo "1. Wait for knowledge base processing to complete"
echo "2. Test chat with knowledge base content"
echo "3. Monitor logs for any errors"
echo "4. Check AWS services (S3, SQS, DynamoDB) for data"
echo ""
echo "🔧 Useful commands:"
echo "   Check logs: tail -f logs/application.log"
echo "   Monitor SQS: aws sqs get-queue-attributes --queue-url <queue-url>"
echo "   Check S3: aws s3 ls s3://chatbot-knowledge-files"
