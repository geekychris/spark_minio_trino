# Kubernetes Iceberg Support

The Kubernetes deployment has been updated to support Apache Iceberg with full Hive Metastore Service (HMS) integration.

## What's New

### Additional Components

1. **PostgreSQL** (`postgres`)
   - Database backend for Hive Metastore
   - Port: 5432 (ClusterIP - internal only)
   - Credentials: hive / hive
   - Database: metastore

2. **Hive Metastore Service** (`hive-metastore`)
   - Centralized metadata management
   - Port: 9083 (ClusterIP - internal only)
   - Supports both Hive (Parquet) and Iceberg tables
   - Uses init containers to download JDBC drivers
   - Startup time: ~60-90 seconds

### Updated Components

3. **Trino**
   - Now has two catalogs:
     - `hive` - For Parquet tables
     - `iceberg` - For Iceberg tables
   - ConfigMaps mounted for catalog configurations

4. **ConfigMaps**
   - Added `hadoop-config` for S3 configuration (core-site.xml)
   - Added Hive and Iceberg catalog properties to `trino-config`

## Deployment

### Quick Deploy
```bash
cd k8s
./deploy.sh
```

This script will:
1. Apply all ConfigMaps
2. Deploy all services
3. Wait for all pods to be ready
4. Display access URLs

### Manual Deploy
```bash
kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml

# Wait for HMS to be ready
kubectl wait --for=condition=available deployment/hive-metastore --timeout=300s
```

### Cleanup
```bash
./cleanup.sh
```

## Architecture

```
┌─────────────┐
│   MinIO     │ ← Object Storage (S3-compatible)
└─────────────┘
      ↑
      │ (data files)
      │
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Spark     │────→│    Iceberg   │────→│    HMS      │
│  (Writer)   │     │   Tables     │     │ (Metadata)  │
└─────────────┘     └──────────────┘     └─────────────┘
                           ↑                     ↑
                           │                     │
                    ┌─────────────┐     ┌─────────────┐
                    │    Trino    │────→│ PostgreSQL  │
                    │  (Reader)   │     │  (Backend)  │
                    └─────────────┘     └─────────────┘
```

## Resource Requirements

### Total Cluster Requirements
- **Memory**: ~8-10Gi minimum
- **CPU**: ~4-5 cores minimum

### Per-Service Breakdown
| Service | Requests | Limits | Notes |
|---------|----------|--------|-------|
| MinIO | 512Mi / 250m | 1Gi / 500m | Object storage |
| PostgreSQL | 512Mi / 250m | 1Gi / 500m | Metastore DB |
| Hive Metastore | 1Gi / 500m | 2Gi / 1000m | 60-90s startup |
| Spark Master | 1Gi / 500m | 2Gi / 1000m | Cluster manager |
| Spark Worker | 2Gi / 1000m | 3Gi / 2000m | Data processing |
| Trino | 2Gi / 1000m | 4Gi / 2000m | Query engine |

## Access Services

Once deployed, access services at:

- **MinIO Console**: http://localhost:30901
  - Username: `admin`
  - Password: `password123`

- **Spark Master UI**: http://localhost:30082

- **Trino**: http://localhost:30081
  ```bash
  trino --server localhost:30081
  ```

## Using Iceberg Tables

### Query Both Catalogs

```sql
-- Show all catalogs
SHOW CATALOGS;

-- Parquet tables (Hive catalog)
USE hive.ecommerce;
SHOW TABLES;
SELECT * FROM customers LIMIT 10;

-- Iceberg tables
USE iceberg.ecommerce;
SHOW TABLES;
SELECT * FROM customers LIMIT 10;
```

### Iceberg-Specific Features

```sql
USE iceberg.ecommerce;

-- View table snapshots
SELECT * FROM "orders$snapshots";

-- View table history
SELECT * FROM "orders$history";

-- View table files
SELECT * FROM "orders$files";

-- Time travel query
SELECT * FROM orders FOR VERSION AS OF 1234567890123456789;

-- Query at specific timestamp
SELECT * FROM orders FOR TIMESTAMP AS OF TIMESTAMP '2024-11-25 10:00:00';
```

## Init Containers

The Hive Metastore deployment uses init containers to ensure proper startup:

1. **wait-for-postgres**: Waits for PostgreSQL to be ready
2. **download-jdbc**: Downloads required JAR files:
   - PostgreSQL JDBC driver
   - Hadoop AWS library
   - AWS SDK bundle

This approach avoids the need for custom Docker images.

## ConfigMap Structure

### `trino-config` ConfigMap
Contains:
- `config.properties` - Trino coordinator settings
- `jvm.config` - JVM memory settings
- `node.properties` - Node configuration
- `hive.properties` - Hive catalog configuration
- `iceberg.properties` - Iceberg catalog configuration

### `hadoop-config` ConfigMap
Contains:
- `core-site.xml` - Hadoop S3 configuration for HMS

## Networking

All services use **ClusterIP** except for user-facing services which use **NodePort**:

| Service | Type | Internal Port | NodePort | Purpose |
|---------|------|---------------|----------|---------|
| MinIO | NodePort | 9000, 9001 | 30900, 30901 | User access |
| PostgreSQL | ClusterIP | 5432 | - | Internal only |
| Hive Metastore | ClusterIP | 9083 | - | Internal only |
| Spark Master | NodePort | 8080, 7077 | 30082, 30077 | User access |
| Spark Worker | ClusterIP | 8081 | - | Internal only |
| Trino | NodePort | 8080 | 30081 | User access |

## Storage Considerations

### Current Setup (Development)
All services use `emptyDir` volumes, which means:
- ✅ Fast and simple
- ❌ Data lost on pod restart
- ❌ Not suitable for production

### Production Recommendations

1. **MinIO**: Use PersistentVolumeClaim (PVC)
   ```yaml
   volumes:
   - name: minio-data
     persistentVolumeClaim:
       claimName: minio-pvc
   ```

2. **PostgreSQL**: Use PVC for data persistence
   ```yaml
   volumes:
   - name: postgres-data
     persistentVolumeClaim:
       claimName: postgres-pvc
   ```

3. **Consider StatefulSets** for stateful services like PostgreSQL

## Troubleshooting

### Hive Metastore Won't Start

**Symptoms**: Pod restarts repeatedly, or liveness probe fails

**Check init containers**:
```bash
kubectl get pods | grep hive-metastore
kubectl logs <pod-name> -c download-jdbc
kubectl logs <pod-name> -c wait-for-postgres
```

**Check main container**:
```bash
kubectl logs <pod-name>
kubectl describe pod <pod-name>
```

**Common issues**:
- PostgreSQL not ready (init container should handle this)
- JDBC driver download failed (check internet connectivity)
- Schema initialization failed (check PostgreSQL credentials)

### Trino Can't Connect to HMS

**Check HMS is running**:
```bash
kubectl get pods -l app=hive-metastore
kubectl logs -l app=hive-metastore | grep "Starting Hive Metastore"
```

**Check service**:
```bash
kubectl get svc hive-metastore
```

**Test connectivity from Trino pod**:
```bash
kubectl exec -it <trino-pod> -- nc -zv hive-metastore 9083
```

### Iceberg Tables Not Visible in Trino

**Check catalog configuration**:
```bash
kubectl exec -it <trino-pod> -- cat /etc/trino/catalog/iceberg.properties
```

**Check HMS logs**:
```bash
kubectl logs -l app=hive-metastore | grep -i error
```

### Out of Memory Errors

**Scale up resources**:
```bash
# Edit deployment and increase resource limits
kubectl edit deployment <deployment-name>

# Or patch directly
kubectl patch deployment trino -p '{"spec":{"template":{"spec":{"containers":[{"name":"trino","resources":{"limits":{"memory":"8Gi"}}}]}}}}'
```

## Scaling

### Scale Spark Workers
```bash
kubectl scale deployment spark-worker --replicas=3
```

### Scale Trino (Single Node → Cluster)
For production, consider a multi-node Trino deployment with coordinator and workers.

## Comparison: Docker Compose vs Kubernetes

| Aspect | Docker Compose | Kubernetes |
|--------|----------------|------------|
| **Deployment** | Simple, single command | More complex, multiple manifests |
| **Scaling** | Manual container creation | Built-in scaling (`kubectl scale`) |
| **Health Checks** | Basic | Advanced (liveness, readiness) |
| **Networking** | Automatic DNS | Service discovery |
| **Storage** | Simple volumes | PVCs, StorageClasses |
| **Init Containers** | Manual scripting | Native support |
| **Resource Limits** | Docker engine limits | Fine-grained per-container |
| **Production Ready** | ❌ No | ✅ Yes |

## Next Steps

1. **Add PersistentVolumes** for data persistence
2. **Configure resource quotas** per namespace
3. **Set up ingress** for external access
4. **Add monitoring** (Prometheus, Grafana)
5. **Implement backup strategy** for PostgreSQL
6. **Configure autoscaling** for Spark workers
7. **Add security** (secrets, RBAC, network policies)

## Summary

The Kubernetes deployment now fully supports Apache Iceberg with:
- ✅ PostgreSQL for metastore persistence
- ✅ Hive Metastore Service for metadata management
- ✅ Both Hive (Parquet) and Iceberg catalogs in Trino
- ✅ Hadoop S3 configuration for HMS
- ✅ Init containers for automatic setup
- ✅ Health checks and proper startup ordering
- ✅ Easy deployment with helper scripts

You can now run the same Iceberg workloads on Kubernetes as you do with Docker Compose!
