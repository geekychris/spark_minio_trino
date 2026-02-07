#!/bin/bash
# Setup script for the Lakehouse demo

set -e

echo "=========================================="
echo "Lakehouse Demo Setup"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

echo "✓ Docker is running"
echo ""

# Create necessary directories
echo "Creating directories..."
mkdir -p spark-apps
mkdir -p spark-data
mkdir -p config
mkdir -p catalog
echo "✓ Directories created"
echo ""

# Start services
echo "Starting services with Docker Compose..."
docker compose up -d

echo ""
echo "Waiting for services to be ready..."
sleep 10

# Check service health
echo ""
echo "Checking service status..."
docker compose ps

echo ""
echo "=========================================="
echo "✓ Setup Complete!"
echo "=========================================="
echo ""
echo "Services started:"
echo "  - MinIO Console:   http://localhost:9001 (admin/password123)"
echo "  - Spark Master UI: http://localhost:8082"
echo "  - Trino Web UI:    http://localhost:8081"
echo ""
echo "Next steps:"
echo "  1. Wait ~30 seconds for Spark to fully initialize"
echo "  2. Run: ./run_spark_app.sh"
echo "  3. Query with Trino: docker exec -it trino trino"
echo ""
echo "For full instructions, see LAKEHOUSE_DEMO.md"
