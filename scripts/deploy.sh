#!/bin/bash

# Deployment script for EC2 instance
# This script is used by GitHub Actions to deploy the application

set -e

# Configuration
APP_NAME="chatbot-app"
CONTAINER_NAME="chatbot-app"
PORT="9090"
AWS_REGION="${AWS_REGION:-us-east-1}"
ECR_REPOSITORY="${ECR_REPOSITORY:-chatbot-app}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
}

warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

# Check if running as root
if [[ $EUID -eq 0 ]]; then
   error "This script should not be run as root"
   exit 1
fi

log "Starting deployment process..."

# Update system packages
log "Updating system packages..."
sudo apt-get update -y

# Install Docker if not already installed
if ! command -v docker &> /dev/null; then
    log "Installing Docker..."
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker $USER
    rm get-docker.sh
    log "Docker installed successfully"
else
    log "Docker is already installed"
fi

# Install Docker Compose if not already installed
if ! command -v docker-compose &> /dev/null; then
    log "Installing Docker Compose..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    log "Docker Compose installed successfully"
else
    log "Docker Compose is already installed"
fi

# Login to ECR
log "Logging into Amazon ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com

# Get ECR registry URL
ECR_REGISTRY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
IMAGE_URI="$ECR_REGISTRY/$ECR_REPOSITORY:latest"

log "ECR Registry: $ECR_REGISTRY"
log "Image URI: $IMAGE_URI"

# Stop existing container if running
if docker ps -q -f name=$CONTAINER_NAME | grep -q .; then
    log "Stopping existing container..."
    docker stop $CONTAINER_NAME
fi

# Remove existing container if it exists
if docker ps -aq -f name=$CONTAINER_NAME | grep -q .; then
    log "Removing existing container..."
    docker rm $CONTAINER_NAME
fi

# Pull latest image
log "Pulling latest image from ECR..."
docker pull $IMAGE_URI

# Check if port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null ; then
    warning "Port $PORT is already in use. Attempting to free it..."
    sudo fuser -k $PORT/tcp || true
    sleep 5
fi

# Run new container
log "Starting new container..."
docker run -d \
    --name $CONTAINER_NAME \
    --restart unless-stopped \
    -p $PORT:$PORT \
    -e AWS_REGION=$AWS_REGION \
    -e OPENAI_API_KEY="$OPENAI_API_KEY" \
    -e PINECONE_API_KEY="$PINECONE_API_KEY" \
    -e PINECONE_API_URL="$PINECONE_API_URL" \
    -e PINECONE_INDEX_NAME="$PINECONE_INDEX_NAME" \
    -e PINECONE_ENVIRONMENT="$PINECONE_ENVIRONMENT" \
    -e PINECONE_PROJECT_ID="$PINECONE_PROJECT_ID" \
    $IMAGE_URI

# Wait for application to start
log "Waiting for application to start..."
sleep 30

# Health check
log "Performing health check..."
MAX_RETRIES=10
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f http://localhost:$PORT/api/v1/health >/dev/null 2>&1; then
        log "Health check passed! Application is running successfully."
        break
    else
        RETRY_COUNT=$((RETRY_COUNT + 1))
        warning "Health check failed. Retry $RETRY_COUNT/$MAX_RETRIES"
        sleep 10
    fi
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    error "Health check failed after $MAX_RETRIES attempts"
    log "Container logs:"
    docker logs $CONTAINER_NAME
    exit 1
fi

# Display container status
log "Container status:"
docker ps -f name=$CONTAINER_NAME

# Display application URL
log "Application is now running at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):$PORT"

log "Deployment completed successfully!"
