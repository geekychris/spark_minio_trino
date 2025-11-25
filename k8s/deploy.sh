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

echo "→ Initializing MinIO buckets..."
kubectl apply -f job-minio-init.yaml
kubectl wait --for=condition=complete job/minio-init --timeout=60s || echo "MinIO init job may need more time"

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
echo "=== Access Services via NodePort ==="
echo ""
echo "Web Interfaces:"
echo "  - MinIO Console:     http://localhost:30901  (admin / password123)"
echo "  - MinIO API:         http://localhost:30900"
echo "  - Spark Master UI:   http://localhost:30082"
echo "  - Spark Worker UI:   http://localhost:30084"
echo "  - Trino Web UI:      http://localhost:30081"
echo ""
echo "Database Connections:"
echo "  - PostgreSQL:        localhost:30432  (hive / hive)"
echo "  - Hive Metastore:    localhost:30083  (thrift)"
echo ""
echo "Connect to Trino:"
echo "  trino --server localhost:30081"
echo ""
echo "Query Iceberg tables:"
echo "  SHOW CATALOGS;"
echo "  USE iceberg.ecommerce;"
echo "  SHOW TABLES;"
echo ""
echo "For detailed access information, see: k8s/ACCESS_GUIDE.md"
echo ""
