#!/bin/bash

set -e

# Configuration
MINIO_ENDPOINT="http://localhost:9000"
MINIO_ROOT_USER="admin"
MINIO_ROOT_PASSWORD="password123"
MINIO_ALIAS="myminio"
BUCKETS=("lakehouse" "warehouse" "iceberg")

# Additional user configuration (optional)
MINIO_USER="datauser"
MINIO_USER_PASSWORD="datapass123"

echo "================================================"
echo "MinIO Setup Script"
echo "================================================"

# Function to check if MinIO CLI (mc) is installed
check_mc_installed() {
    if ! command -v mc &> /dev/null; then
        echo "ERROR: MinIO client (mc) is not installed."
        echo "Please install it from: https://min.io/docs/minio/linux/reference/minio-mc.html"
        echo "Or run: brew install minio-mc"
        exit 1
    fi
    echo "✓ MinIO CLI (mc) is installed"
}

# Function to check if Docker is running
check_docker() {
    if ! docker info &> /dev/null; then
        echo "ERROR: Docker is not running. Please start Docker."
        exit 1
    fi
    echo "✓ Docker is running"
}

# Function to start Docker Compose services
start_services() {
    echo ""
    echo "Starting Docker Compose services..."
    docker compose up -d minio postgres
    
    echo "Waiting for MinIO to be healthy..."
    for i in {1..30}; do
        if docker compose ps minio | grep -q "healthy"; then
            echo "✓ MinIO is healthy"
            return 0
        fi
        echo "Waiting... ($i/30)"
        sleep 2
    done
    
    echo "ERROR: MinIO failed to become healthy"
    exit 1
}

# Function to configure MinIO alias
configure_alias() {
    echo ""
    echo "Configuring MinIO alias..."
    
    # Remove existing alias if it exists
    mc alias remove "$MINIO_ALIAS" 2>/dev/null || true
    
    # Set new alias
    mc alias set "$MINIO_ALIAS" "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
    
    if [ $? -eq 0 ]; then
        echo "✓ MinIO alias '$MINIO_ALIAS' configured successfully"
    else
        echo "ERROR: Failed to configure MinIO alias"
        exit 1
    fi
}

# Function to create buckets
create_buckets() {
    echo ""
    echo "Creating buckets..."
    
    for bucket in "${BUCKETS[@]}"; do
        if mc ls "$MINIO_ALIAS/$bucket" &> /dev/null; then
            echo "✓ Bucket '$bucket' already exists"
        else
            mc mb "$MINIO_ALIAS/$bucket"
            echo "✓ Created bucket '$bucket'"
        fi
    done
}

# Function to create additional user with policy
create_user() {
    echo ""
    echo "Creating MinIO user..."
    
    # Create user
    mc admin user add "$MINIO_ALIAS" "$MINIO_USER" "$MINIO_USER_PASSWORD" 2>/dev/null || {
        echo "✓ User '$MINIO_USER' already exists or created"
    }
    
    # Create a custom policy for the user (readwrite access to all buckets)
    cat > /tmp/minio-user-policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetBucketLocation",
                "s3:ListBucket",
                "s3:ListBucketMultipartUploads"
            ],
            "Resource": [
                "arn:aws:s3:::lakehouse",
                "arn:aws:s3:::warehouse",
                "arn:aws:s3:::iceberg"
            ]
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:AbortMultipartUpload",
                "s3:DeleteObject",
                "s3:GetObject",
                "s3:ListMultipartUploadParts",
                "s3:PutObject"
            ],
            "Resource": [
                "arn:aws:s3:::lakehouse/*",
                "arn:aws:s3:::warehouse/*",
                "arn:aws:s3:::iceberg/*"
            ]
        }
    ]
}
EOF
    
    # Add policy
    mc admin policy create "$MINIO_ALIAS" datauser-policy /tmp/minio-user-policy.json 2>/dev/null || {
        echo "✓ Policy 'datauser-policy' already exists"
    }
    
    # Attach policy to user
    mc admin policy attach "$MINIO_ALIAS" datauser-policy --user="$MINIO_USER"
    
    echo "✓ User '$MINIO_USER' configured with policy"
    
    # Clean up temp file
    rm /tmp/minio-user-policy.json
}

# Function to verify setup
verify_setup() {
    echo ""
    echo "Verifying setup..."
    
    # List buckets
    echo "Buckets:"
    mc ls "$MINIO_ALIAS"
    
    # List users
    echo ""
    echo "Users:"
    mc admin user list "$MINIO_ALIAS"
    
    echo ""
    echo "✓ Setup verification complete"
}

# Function to display connection info
display_info() {
    echo ""
    echo "================================================"
    echo "MinIO Setup Complete!"
    echo "================================================"
    echo "Console URL:    http://localhost:9001"
    echo "API Endpoint:   $MINIO_ENDPOINT"
    echo ""
    echo "Root Credentials:"
    echo "  Username:     $MINIO_ROOT_USER"
    echo "  Password:     $MINIO_ROOT_PASSWORD"
    echo ""
    echo "User Credentials:"
    echo "  Username:     $MINIO_USER"
    echo "  Password:     $MINIO_USER_PASSWORD"
    echo ""
    echo "Buckets created: ${BUCKETS[*]}"
    echo ""
    echo "MC Alias configured: $MINIO_ALIAS"
    echo ""
    echo "To use the MinIO CLI:"
    echo "  mc ls $MINIO_ALIAS"
    echo "  mc cp myfile.txt $MINIO_ALIAS/lakehouse/"
    echo "================================================"
}

# Main execution
main() {
    check_mc_installed
    check_docker
    start_services
    configure_alias
    create_buckets
    create_user
    verify_setup
    display_info
}

# Run main function
main
