#!/bin/bash

echo "=== Testing Docker Build Locally ==="

# Step 1: Build the application locally (we know this works)
echo "1. Building application with Maven..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Maven build failed"
    exit 1
fi

echo "✅ Maven build successful"

# Step 2: Create a simple Dockerfile for testing
echo "2. Creating test Dockerfile..."
cat > Dockerfile.test << 'EOF'
FROM openjdk:17-jdk-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/chatbot-0.0.1-SNAPSHOT.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 9090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9090/api/v1/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Step 3: Build Docker image
echo "3. Building Docker image..."
docker build -f Dockerfile.test -t chatbot-app-test .

if [ $? -ne 0 ]; then
    echo "❌ Docker build failed"
    exit 1
fi

echo "✅ Docker build successful"

# Step 4: Test the container
echo "4. Testing container..."
docker run -d --name chatbot-test -p 9090:9090 chatbot-app-test

# Wait for startup
echo "Waiting for application to start..."
sleep 30

# Test health endpoint
echo "5. Testing health endpoint..."
if curl -f http://localhost:9090/api/v1/health; then
    echo "✅ Application is running and healthy!"
else
    echo "❌ Health check failed"
    docker logs chatbot-test
fi

# Cleanup
echo "6. Cleaning up..."
docker stop chatbot-test
docker rm chatbot-test
rm Dockerfile.test

echo "=== Test Complete ==="
