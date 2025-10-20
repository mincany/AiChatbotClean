# CI/CD Setup Guide for Chatbot Application

This guide will help you set up a complete CI/CD pipeline for your Spring Boot chatbot application using GitHub Actions, AWS ECR, and EC2 deployment.

## 🚀 Overview

The CI/CD pipeline will automatically:
1. **Run tests** on every push/PR
2. **Build Docker image** and push to AWS ECR
3. **Deploy to EC2** instance
4. **Update running container** with zero downtime

## 📋 Prerequisites

### 1. AWS Account Setup
- AWS account with appropriate permissions
- EC2 instance running Ubuntu 20.04+ 
- AWS CLI configured
- ECR repository created

### 2. GitHub Repository
- Repository with your chatbot code
- GitHub Actions enabled
- Required secrets configured

### 3. EC2 Instance Requirements
- Ubuntu 20.04+ (recommended)
- At least 2GB RAM, 10GB storage
- Security group allowing SSH (22) and HTTP (9090)
- IAM role with ECR permissions

## 🔧 Step-by-Step Setup

### Step 1: Prepare EC2 Instance

1. **Launch EC2 instance** with the following specifications:
   - **AMI**: Ubuntu Server 20.04 LTS
   - **Instance Type**: t3.small or larger
   - **Storage**: 20GB GP3
   - **Security Group**: Allow SSH (22) and HTTP (9090)

2. **Connect to your EC2 instance**:
   ```bash
   ssh -i your-key.pem ubuntu@your-ec2-ip
   ```

3. **Run the setup script**:
   ```bash
   # Download and run the setup script
   curl -fsSL https://raw.githubusercontent.com/your-username/your-repo/main/scripts/setup-ec2.sh | bash
   
   # Or if you have the script locally
   chmod +x scripts/setup-ec2.sh
   ./scripts/setup-ec2.sh
   ```

4. **Configure AWS credentials**:
   ```bash
   aws configure
   # Enter your AWS Access Key ID, Secret Access Key, and region
   ```

### Step 2: Create ECR Repository

1. **Create ECR repository**:
   ```bash
   aws ecr create-repository \
       --repository-name chatbot-app \
       --region us-east-1
   ```

2. **Note the repository URI** (you'll need this for GitHub secrets):
   ```
   <account-id>.dkr.ecr.us-east-1.amazonaws.com/chatbot-app
   ```

### Step 3: Configure GitHub Secrets

Go to your GitHub repository → Settings → Secrets and variables → Actions, and add the following secrets:

#### Required Secrets:
- `AWS_ACCESS_KEY_ID`: Your AWS access key
- `AWS_SECRET_ACCESS_KEY`: Your AWS secret key
- `EC2_HOST`: Your EC2 instance public IP
- `EC2_USERNAME`: Usually `ubuntu` for Ubuntu instances
- `EC2_SSH_KEY`: Your private SSH key content

#### Application Secrets:
- `OPENAI_API_KEY`: Your OpenAI API key
- `PINECONE_API_KEY`: Your Pinecone API key
- `PINECONE_API_URL`: Your Pinecone API URL
- `PINECONE_INDEX_NAME`: Your Pinecone index name
- `PINECONE_ENVIRONMENT`: Your Pinecone environment
- `PINECONE_PROJECT_ID`: Your Pinecone project ID

### Step 4: Configure Application Properties

Update your `application.properties` to use environment variables for production:

```properties
# Production configuration using environment variables
server.port=9090
spring.application.name=chatbot-poc

# OpenAI Configuration
openai.api-key=${OPENAI_API_KEY}
openai.api-url=https://api.openai.com/v1

# Pinecone Configuration
pinecone.api-key=${PINECONE_API_KEY}
pinecone.api-url=${PINECONE_API_URL}
pinecone.index-name=${PINECONE_INDEX_NAME}
pinecone.environment=${PINECONE_ENVIRONMENT}
pinecone.project-id=${PINECONE_PROJECT_ID}

# AWS Configuration (using IAM role)
aws.region=${AWS_REGION:us-east-1}
spring.cloud.aws.credentials.use-default-aws-credentials-chain=true
spring.cloud.aws.stack.auto=false

# DynamoDB Tables
aws.dynamodb.users-table=chatbot-users
aws.dynamodb.knowledge-table=chatbot-knowledge
aws.dynamodb.conversations-table=chatbot-conversations
aws.dynamodb.messages-table=chatbot-messages

# S3 Configuration
aws.s3.bucket-name=chatbot-knowledge-files
aws.s3.region=${AWS_REGION:us-east-1}

# SQS Configuration
aws.sqs.knowledge-processing-queue=knowledge-processing-queue
aws.sqs.region=${AWS_REGION:us-east-1}

# Logging
logging.level.com.example.chatbot=INFO
logging.level.org.springframework.web=INFO
```

## 🚀 Deployment Process

### Automatic Deployment

Once everything is configured, deployment happens automatically:

1. **Make changes locally**:
   ```bash
   git add .
   git commit -m "Feature: Add new endpoint"
   git push origin main
   ```

2. **GitHub Actions automatically**:
   - Runs tests
   - Builds Docker image
   - Pushes to ECR
   - Deploys to EC2
   - Updates running container

### Manual Deployment

If you need to deploy manually:

1. **SSH into your EC2 instance**:
   ```bash
   ssh -i your-key.pem ubuntu@your-ec2-ip
   ```

2. **Run the deployment script**:
   ```bash
   cd /opt/chatbot
   ./deploy.sh
   ```

3. **Check deployment status**:
   ```bash
   ./monitor.sh
   ```

## 🔍 Monitoring and Troubleshooting

### Health Checks

1. **Application health**:
   ```bash
   curl http://your-ec2-ip:9090/api/v1/health
   ```

2. **Container status**:
   ```bash
   docker ps
   docker logs chatbot-app
   ```

3. **System monitoring**:
   ```bash
   cd /opt/chatbot
   ./monitor.sh
   ```

### Common Issues

#### 1. Container Won't Start
```bash
# Check container logs
docker logs chatbot-app

# Check if port is in use
sudo lsof -i :9090

# Restart container
docker restart chatbot-app
```

#### 2. ECR Login Issues
```bash
# Re-login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
```

#### 3. Application Health Check Fails
```bash
# Check if all environment variables are set
docker exec chatbot-app env | grep -E "(OPENAI|PINECONE|AWS)"

# Check application logs
docker logs chatbot-app --tail 50
```

### Log Management

1. **View application logs**:
   ```bash
   docker logs -f chatbot-app
   ```

2. **View system logs**:
   ```bash
   sudo journalctl -u chatbot-app -f
   ```

3. **Backup logs**:
   ```bash
   cd /opt/chatbot
   ./backup.sh
   ```

## 🔄 CI/CD Pipeline Details

### Workflow Triggers
- **Push to main/master**: Full deployment
- **Pull requests**: Test only
- **Manual trigger**: Available in GitHub Actions

### Pipeline Stages

1. **Test Stage**:
   - Checkout code
   - Setup Java 17
   - Cache Maven dependencies
   - Run tests
   - Generate test reports

2. **Build Stage**:
   - Configure AWS credentials
   - Login to ECR
   - Build Docker image
   - Push to ECR with tags

3. **Deploy Stage**:
   - SSH to EC2 instance
   - Stop existing container
   - Pull latest image
   - Start new container
   - Health check

4. **Notify Stage**:
   - Success/failure notifications
   - Deployment status

### Environment Variables

The pipeline uses these environment variables:
- `AWS_REGION`: us-east-1
- `ECR_REPOSITORY`: chatbot-app
- `ECS_SERVICE`: chatbot-service
- `ECS_CLUSTER`: chatbot-cluster

## 📊 Monitoring Dashboard

### Application Metrics
- **Health endpoint**: `http://your-ec2-ip:9090/api/v1/health`
- **Container status**: `docker ps`
- **Resource usage**: `htop` or `docker stats`

### Log Aggregation
- **Application logs**: `docker logs chatbot-app`
- **System logs**: `journalctl -u chatbot-app`
- **Access logs**: Configure nginx/apache if needed

## 🔒 Security Considerations

### 1. IAM Permissions
Ensure your EC2 instance has minimal required permissions:
- ECR read access
- CloudWatch logs
- DynamoDB access
- S3 access
- SQS access

### 2. Network Security
- Use security groups to restrict access
- Consider using VPC for production
- Enable HTTPS with SSL certificates

### 3. Secrets Management
- Use AWS Secrets Manager for sensitive data
- Rotate API keys regularly
- Monitor access logs

## 🚀 Scaling Considerations

### Horizontal Scaling
- Use Application Load Balancer
- Multiple EC2 instances
- Auto Scaling Groups
- Container orchestration (ECS/EKS)

### Vertical Scaling
- Increase instance size
- Optimize JVM settings
- Database connection pooling
- Caching strategies

## 📝 Maintenance

### Regular Tasks
1. **Update dependencies**: Monthly
2. **Security patches**: As needed
3. **Backup verification**: Weekly
4. **Log rotation**: Automatic
5. **Health monitoring**: Continuous

### Backup Strategy
- Application backups: Daily
- Database backups: As per AWS DynamoDB
- Configuration backups: With each deployment
- Log retention: 30 days

## 🆘 Support

### Getting Help
1. Check application logs: `docker logs chatbot-app`
2. Check system logs: `journalctl -u chatbot-app`
3. Verify environment variables
4. Test connectivity to AWS services
5. Check GitHub Actions logs

### Emergency Procedures
1. **Rollback**: Use previous Docker image
2. **Restart**: `sudo systemctl restart chatbot-app`
3. **Scale**: Add more instances if needed
4. **Recovery**: Restore from backups

---

## 🎉 You're All Set!

Your CI/CD pipeline is now configured! Every time you push to the main branch, your application will be automatically tested, built, and deployed to your EC2 instance.

**Next steps:**
1. Push your code to trigger the first deployment
2. Monitor the GitHub Actions workflow
3. Verify your application is running
4. Test the health endpoint
5. Start developing with confidence!

Happy coding! 🚀
