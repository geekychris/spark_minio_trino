# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the complete lakehouse stack with MinIO, Spark, and Trino.

## Components

### MinIO (Object Storage)
- **API Port**: 9000 (NodePort: 30900)
- **Console Port**: 9001 (NodePort: 30901)
- **Credentials**: admin / password123

### PostgreSQL (Metastore Database)
- **Port**: 5432 (ClusterIP - internal only)
- **Database**: metastore
- **Credentials**: hive / hive

### Hive Metastore Service (HMS)
- **Thrift Port**: 9083 (ClusterIP - internal only)
- Provides metadata management for Hive and Iceberg tables
- Connected to PostgreSQL for persistence

### Spark Master
- **Web UI Port**: 8080 (NodePort: 30082)
- **Master Port**: 7077 (NodePort: 30077)

### Spark Worker
- **Service Type**: ClusterIP (internal only)
- Connects to spark-master:7077

### Trino
- **HTTP Port**: 8080 (NodePort: 30081)
- **Catalogs**: hive (Parquet tables), iceberg (Iceberg tables)

## Deployment

### Deploy All Services
```bash
# Apply ConfigMaps first (contains Trino and Hadoop configs)
kubectl apply -f configmap.yaml

# Deploy services
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### Verify Deployment
```bash
kubectl get deployments
kubectl get services
kubectl get pods

# Wait for all pods to be ready (especially hive-metastore takes ~90s)
kubectl wait --for=condition=ready pod -l app=hive-metastore --timeout=300s
```

## Access Services on Host Machine

Once deployed, you can access the services on your host machine at:

- **MinIO API**: http://localhost:30900
- **MinIO Console**: http://localhost:30901
- **Spark Master UI**: http://localhost:30082
- **Trino**: http://localhost:30081

### MinIO Console Login
Navigate to http://localhost:30901 and login with:
- Username: `admin`
- Password: `password123`

### Spark Master UI
Navigate to http://localhost:30082 to view the Spark cluster status and running jobs.

### Trino UI
Navigate to http://localhost:30081 to access the Trino web interface.

## Connecting to Trino

Using the Trino CLI:
```bash
trino --server localhost:30081
```

### Query Iceberg Tables

```sql
-- Show available catalogs (hive and iceberg)
SHOW CATALOGS;

-- Parquet tables (Hive catalog)
SHOW SCHEMAS IN hive;
USE hive.ecommerce;
SHOW TABLES;
SELECT * FROM customers LIMIT 10;

-- Iceberg tables
SHOW SCHEMAS IN iceberg;
USE iceberg.ecommerce;
SHOW TABLES;
SELECT * FROM customers LIMIT 10;

-- Iceberg-specific features
SELECT * FROM "orders$snapshots";  -- View snapshots
SELECT * FROM "orders$history";     -- View history
SELECT * FROM orders FOR VERSION AS OF <snapshot-id>;  -- Time travel
```

## Resource Requirements

### MinIO
- Requests: 512Mi memory, 250m CPU
- Limits: 1Gi memory, 500m CPU

### PostgreSQL
- Requests: 512Mi memory, 250m CPU
- Limits: 1Gi memory, 500m CPU

### Hive Metastore
- Requests: 1Gi memory, 500m CPU
- Limits: 2Gi memory, 1000m CPU
- Startup time: ~60-90 seconds

### Spark Master
- Requests: 1Gi memory, 500m CPU
- Limits: 2Gi memory, 1000m CPU

### Spark Worker
- Requests: 2Gi memory, 1000m CPU
- Limits: 3Gi memory, 2000m CPU
- Worker Memory: 2g
- Worker Cores: 2

### Trino
- Requests: 2Gi memory, 1000m CPU
- Limits: 4Gi memory, 2000m CPU

**Total Cluster Requirements:**
- ~8-10Gi memory minimum
- ~4-5 CPU cores minimum

## Storage

All services currently use `emptyDir` volumes, which means data will be lost when pods restart. For production use, consider:

1. **MinIO**: Use PersistentVolumeClaim (PVC) for `/data`
2. **Spark**: Use PVCs or ConfigMaps for application code and data

## Scaling

To scale Spark workers:
```bash
kubectl scale deployment spark-worker --replicas=3
```

## Cleanup

To remove all resources:
```bash
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
```

## Troubleshooting

### Check Pod Logs
```bash
kubectl logs -f deployment/minio
kubectl logs -f deployment/postgres
kubectl logs -f deployment/hive-metastore
kubectl logs -f deployment/spark-master
kubectl logs -f deployment/spark-worker
kubectl logs -f deployment/trino
```

### Hive Metastore Not Starting
The HMS takes time to download JDBC drivers and initialize:
```bash
# Check init container logs
kubectl logs <hive-metastore-pod> -c download-jdbc

# Check if schema was initialized
kubectl logs <hive-metastore-pod> | grep "Initialized schema"
```

### Check Pod Status
```bash
kubectl describe pod <pod-name>
```

### Port Forward (Alternative to NodePort)
If NodePort isn't working, you can use port-forward:
```bash
kubectl port-forward service/minio 9000:9000 9001:9001
kubectl port-forward service/spark-master 8082:8080
kubectl port-forward service/trino 8081:8080
```

## Notes

- NodePort range is typically 30000-32767 in Kubernetes
- The Spark worker doesn't need external access, so it uses ClusterIP
- Health checks are configured for MinIO and Trino to ensure proper startup
- All services are in the default namespace (modify with `-n <namespace>` if needed)
