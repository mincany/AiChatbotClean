#!/bin/bash

# EC2 Setup script for chatbot application
# Run this script on your EC2 instance to prepare it for deployment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}"
}

warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

info() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

# Check if running on supported systems
if ! command -v apt-get &> /dev/null && ! command -v dnf &> /dev/null && ! command -v yum &> /dev/null; then
    error "This script is designed for Ubuntu/Debian, Amazon Linux, or CentOS/RHEL systems"
    exit 1
fi

# Detect the package manager
if command -v apt-get &> /dev/null; then
    PACKAGE_MANAGER="apt"
elif command -v dnf &> /dev/null; then
    PACKAGE_MANAGER="dnf"
elif command -v yum &> /dev/null; then
    PACKAGE_MANAGER="yum"
else
    error "Unsupported package manager"
    exit 1
fi

log "Detected package manager: $PACKAGE_MANAGER"

log "Starting EC2 setup for chatbot application..."

# Update system packages
log "Updating system packages..."
if [ "$PACKAGE_MANAGER" = "apt" ]; then
    sudo apt-get update -y
    sudo apt-get upgrade -y
elif [ "$PACKAGE_MANAGER" = "dnf" ]; then
    sudo dnf update -y
elif [ "$PACKAGE_MANAGER" = "yum" ]; then
    sudo yum update -y
fi

# Install essential packages
log "Installing essential packages..."
if [ "$PACKAGE_MANAGER" = "apt" ]; then
    sudo apt-get install -y \
        curl \
        wget \
        git \
        unzip \
        jq \
        htop \
        vim \
        ufw
elif [ "$PACKAGE_MANAGER" = "dnf" ]; then
    sudo dnf -y swap curl-minimal curl || true

    sudo dnf install -y \
        curl \
        wget \
        git \
        unzip \
        jq \
        htop \
        vim \
        firewalld \
        cronie
elif [ "$PACKAGE_MANAGER" = "yum" ]; then
    sudo yum install -y \
        curl \
        wget \
        git \
        unzip \
        jq \
        htop \
        vim \
        firewalld \
        cronie
fi

# Install AWS CLI v2
if ! command -v aws &> /dev/null; then
    log "Installing AWS CLI v2..."
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    unzip awscliv2.zip
    sudo ./aws/install
    rm -rf awscliv2.zip aws/
    log "AWS CLI installed successfully"
else
    log "AWS CLI is already installed"
fi

# Install Docker
if ! command -v docker &> /dev/null; then
    log "Installing Docker..."

    if [ "$PACKAGE_MANAGER" = "dnf" ] || [ "$PACKAGE_MANAGER" = "yum" ]; then
        sudo yum install -y docker
        sudo systemctl enable --now docker
        sudo usermod -aG docker $USER
        log "Docker installed successfully (Amazon Linux)"
    else
        # Ubuntu/Debian path (keep yours)
        curl -fsSL https://get.docker.com -o get-docker.sh
        sudo sh get-docker.sh
        sudo usermod -aG docker $USER
        rm get-docker.sh
        log "Docker installed successfully"
    fi
else
    log "Docker is already installed"
fi

# Install Docker Compose
if ! command -v docker-compose &> /dev/null; then
    log "Installing Docker Compose..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    log "Docker Compose installed successfully 222"
else
    log "Docker Compose is already installed"
fi

# Configure firewall
log "Configuring firewall..."
if [ "$PACKAGE_MANAGER" = "apt" ]; then
    sudo ufw --force enable
    sudo ufw allow ssh
    sudo ufw allow 9090/tcp
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
elif [ "$PACKAGE_MANAGER" = "dnf" ] || [ "$PACKAGE_MANAGER" = "yum" ]; then
    sudo systemctl start firewalld
    sudo systemctl enable firewalld
    sudo firewall-cmd --permanent --add-port=22/tcp
    sudo firewall-cmd --permanent --add-port=9090/tcp
    sudo firewall-cmd --permanent --add-port=80/tcp
    sudo firewall-cmd --permanent --add-port=443/tcp
    sudo firewall-cmd --reload
fi
log "Firewall configured"

# Create application directory
APP_DIR="/opt/chatbot"
log "Creating application directory: $APP_DIR"
sudo mkdir -p $APP_DIR
sudo chown $USER:$USER $APP_DIR

# Create deployment script
log "Creating deployment script..."
cat > $APP_DIR/deploy.sh << 'EOF'
#!/bin/bash
# Auto-generated deployment script
set -e

APP_NAME="chatbot-app"
CONTAINER_NAME="chatbot-app"
PORT="9090"
AWS_REGION="${AWS_REGION:-us-east-1}"
ECR_REPOSITORY="${ECR_REPOSITORY:-chatbot-app}"

# Get ECR registry URL
ECR_REGISTRY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
IMAGE_URI="$ECR_REGISTRY/$ECR_REPOSITORY:latest"

echo "Deploying $IMAGE_URI..."

# Stop existing container
docker stop $CONTAINER_NAME || true
docker rm $CONTAINER_NAME || true

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY

# Pull and run latest image
docker pull $IMAGE_URI
docker run -d \
    --name $CONTAINER_NAME \
    --restart unless-stopped \
    -p $PORT:$PORT \
    -e AWS_REGION=$AWS_REGION \
    $IMAGE_URI

echo "Deployment completed!"
EOF

chmod +x $APP_DIR/deploy.sh

# Create systemd service for the application
log "Creating systemd service..."
sudo tee /etc/systemd/system/chatbot-app.service > /dev/null << EOF
[Unit]
Description=Chatbot Application
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$APP_DIR
ExecStart=$APP_DIR/deploy.sh
ExecStop=/usr/bin/docker stop chatbot-app
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd and enable service
sudo systemctl daemon-reload
sudo systemctl enable chatbot-app.service

# Create monitoring script
log "Creating monitoring script..."
cat > $APP_DIR/monitor.sh << 'EOF'
#!/bin/bash
# Application monitoring script

APP_NAME="chatbot-app"
PORT="9090"

echo "=== Chatbot Application Status ==="
echo "Date: $(date)"
echo ""

# Check if container is running
if docker ps -q -f name=$APP_NAME | grep -q .; then
    echo "✅ Container is running"
    echo "Container ID: $(docker ps -q -f name=$APP_NAME)"
    echo "Container Status: $(docker ps --format 'table {{.Status}}' -f name=$APP_NAME | tail -n +2)"
else
    echo "❌ Container is not running"
fi

echo ""

# Check port
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null; then
    echo "✅ Port $PORT is listening"
else
    echo "❌ Port $PORT is not listening"
fi

echo ""

# Health check
if curl -f http://localhost:$PORT/api/v1/health >/dev/null 2>&1; then
    echo "✅ Application health check passed"
else
    echo "❌ Application health check failed"
fi

echo ""

# Show recent logs
echo "=== Recent Logs ==="
docker logs --tail 10 $APP_NAME 2>/dev/null || echo "No logs available"
EOF

chmod +x $APP_DIR/monitor.sh

# Create log rotation configuration
log "Setting up log rotation..."
sudo tee /etc/logrotate.d/docker-containers > /dev/null << EOF
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    size=1M
    missingok
    delaycompress
    copytruncate
}
EOF

# Set up log monitoring
log "Setting up log monitoring..."
# Amazon Linux 2023 uses systemd-journald instead of rsyslog
# Configure journald for better Docker log handling
sudo mkdir -p /etc/systemd/journald.conf.d
sudo tee /etc/systemd/journald.conf.d/docker.conf > /dev/null << EOF
[Journal]
# Increase log storage
SystemMaxUse=1G
SystemMaxFileSize=100M
# Keep logs for 30 days
MaxRetentionSec=2592000
# Forward to syslog if needed
ForwardToSyslog=yes
EOF

# Restart journald to apply configuration
sudo systemctl restart systemd-journald

# Create backup script
log "Creating backup script..."
cat > $APP_DIR/backup.sh << 'EOF'
#!/bin/bash
# Backup script for chatbot application

BACKUP_DIR="/opt/backups/chatbot"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="chatbot_backup_$DATE.tar.gz"

mkdir -p $BACKUP_DIR

# Backup application configuration
tar -czf $BACKUP_DIR/$BACKUP_FILE \
    /opt/chatbot \
    /etc/systemd/system/chatbot-app.service \
    /etc/logrotate.d/docker-containers \
    /etc/systemd/journald.conf.d/docker.conf

echo "Backup created: $BACKUP_DIR/$BACKUP_FILE"

# Keep only last 7 backups
find $BACKUP_DIR -name "chatbot_backup_*.tar.gz" -mtime +7 -delete
EOF

chmod +x $APP_DIR/backup.sh

# Set up cron job for backups
log "Setting up automated backups..."
# Start and enable cron service
sudo systemctl start crond
sudo systemctl enable crond
(crontab -l 2>/dev/null; echo "0 2 * * * $APP_DIR/backup.sh") | crontab -

# Display system information
log "EC2 setup completed successfully!"
echo ""
info "=== System Information ==="
echo "OS: $(cat /etc/os-release | grep PRETTY_NAME | cut -d'=' -f2 | tr -d '\"')"
echo "Kernel: $(uname -r)"
echo "Memory: $(free -h | awk '/^Mem:/ {print $2}')"
echo "Disk: $(df -h / | awk 'NR==2 {print $4}')"
echo "Public IP: $(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)"
echo ""

info "=== Installed Software ==="
echo "Docker: $(docker --version)"
echo "Docker Compose: $(docker-compose --version)"
echo "AWS CLI: $(aws --version)"
echo ""

info "=== Next Steps ==="
echo "1. Configure AWS credentials: aws configure"
echo "2. Create ECR repository: aws ecr create-repository --repository-name chatbot-app"
echo "3. Set up GitHub Actions secrets"
echo "4. Push your code to trigger deployment"
echo ""

info "=== Useful Commands ==="
echo "Monitor application: $APP_DIR/monitor.sh"
echo "View logs: docker logs chatbot-app"
echo "Restart application: sudo systemctl restart chatbot-app"
echo "Check status: sudo systemctl status chatbot-app"
echo ""

log "Setup completed! Your EC2 instance is ready for deployment."
