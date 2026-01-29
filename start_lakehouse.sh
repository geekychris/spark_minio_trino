#!/bin/bash
# Lakehouse Demo Startup Script
# This script starts all services and generates sample data

set -e

echo "=========================================="
echo "Starting Lakehouse Demo Environment"
echo "=========================================="
echo ""

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Start core infrastructure
echo -e "${YELLOW}Step 1/5: Starting core infrastructure (MinIO, Postgres)...${NC}"
docker compose up -d minio postgres
echo "Waiting for services to become healthy..."
sleep 10

# Step 2: Initialize MinIO buckets
echo -e "${YELLOW}Step 2/5: Setting up MinIO buckets...${NC}"
docker compose up minio-init
sleep 3

# Step 3: Start Hive Metastore
echo -e "${YELLOW}Step 3/5: Starting Hive Metastore...${NC}"
docker compose up -d hive-metastore
echo "Waiting for Hive Metastore to become healthy (this may take up to 90 seconds)..."
sleep 30

# Step 4: Start Spark cluster
echo -e "${YELLOW}Step 4/5: Starting Spark cluster...${NC}"
docker compose up -d spark-master spark-worker
echo "Waiting for Spark cluster to be ready..."
sleep 10

# Step 5: Start streaming services (Kafka, Zookeeper) and Trino
echo -e "${YELLOW}Step 5/5: Starting Kafka, Zookeeper, and Trino...${NC}"
docker compose up -d zookeeper kafka trino
echo "Waiting for all services to stabilize..."
sleep 15

echo ""
echo -e "${GREEN}=========================================="
echo "All services started successfully!"
echo "==========================================${NC}"
echo ""

# Display service status
echo "Service Access:"
echo ""
echo "Web UIs:"
echo "  • MinIO Console:      http://localhost:9001"
echo "                        Login: admin / password123"
echo ""
echo "  • Spark Master UI:    http://localhost:8082"
echo "                        (Cluster status, running jobs)"
echo ""
echo "  • Trino Web UI:       http://localhost:8081"
echo "                        (Query history, cluster info)"
echo ""
echo "Service Ports:"
echo "  • Trino:              localhost:8081"
echo "  • Spark Master:       localhost:7077"
echo "  • Hive Metastore:     localhost:9083"
echo "  • MinIO S3 API:       localhost:9000"
echo "  • Kafka:              localhost:9092"
echo "  • Zookeeper:          localhost:2181"
echo "  • PostgreSQL:         localhost:5432"
echo ""

# Generate sample data
echo -e "${YELLOW}Generating sample e-commerce data...${NC}"
echo ""
./run_spark_app.sh

echo ""
echo -e "${GREEN}=========================================="
echo "Setup Complete!"
echo "==========================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Query data with Trino:"
echo "     docker exec -it trino trino"
echo "     trino> SHOW SCHEMAS IN hive;"
echo "     trino> USE hive.ecommerce;"
echo "     trino> SHOW TABLES;"
echo "     trino> SELECT * FROM customers LIMIT 10;"
echo ""
echo "  2. View all running containers:"
echo "     docker compose ps"
echo ""
echo "  3. Stop all services:"
echo "     docker compose down"
echo ""
