#!/bin/bash
# Deploy lakehouse stack with Iceberg support to Kubernetes

set -e

echo "Deploying lakehouse stack with Iceberg support to Kubernetes..."
echo ""

# Apply ConfigMaps
echo "→ Applying ConfigMaps..."
kubectl apply -f configmap.yaml

# Apply Deployments
echo "→ Applying Deployments..."
kubectl apply -f deployment.yaml

# Apply Services
echo "→ Applying Services..."
kubectl apply -f service.yaml

echo ""
echo "✓ Deployment initiated!"
echo ""
echo "Waiting for deployments to be ready..."
echo ""

# Wait for critical services
echo "→ Waiting for MinIO..."
kubectl wait --for=condition=available deployment/minio --timeout=60s

echo "→ Waiting for PostgreSQL..."
kubectl wait --for=condition=available deployment/postgres --timeout=60s

echo "→ Waiting for Hive Metastore (this takes ~90 seconds)..."
kubectl wait --for=condition=available deployment/hive-metastore --timeout=300s

echo "→ Waiting for Spark Master..."
kubectl wait --for=condition=available deployment/spark-master --timeout=60s

echo "→ Waiting for Spark Worker..."
kubectl wait --for=condition=available deployment/spark-worker --timeout=60s

echo "→ Waiting for Trino..."
kubectl wait --for=condition=available deployment/trino --timeout=120s

echo ""
echo "✓ All services are ready!"
echo ""
echo "Access services at:"
echo "  - MinIO Console: http://localhost:30901 (admin / password123)"
echo "  - Spark Master UI: http://localhost:30082"
echo "  - Trino: http://localhost:30081"
echo ""
echo "Connect to Trino:"
echo "  trino --server localhost:30081"
echo ""
echo "Query Iceberg tables:"
echo "  SHOW CATALOGS;"
echo "  USE iceberg.ecommerce;"
echo "  SHOW TABLES;"
echo ""
