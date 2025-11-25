# Kubernetes Deployment Summary

This document summarizes all Kubernetes resources for the lakehouse stack.

## Overview

All Docker Compose services have been converted to Kubernetes resources with NodePort access enabled for external connectivity from the host machine.

## Resources Created

### Deployments (`deployment.yaml`)
- **MinIO**: Object storage (S3-compatible)
- **PostgreSQL**: Database for Hive Metastore
- **Hive Metastore**: Table metadata service
- **Spark Master**: Spark cluster coordinator
- **Spark Worker**: Spark executor node
- **Trino**: Distributed SQL query engine

### Services (`service.yaml`)
All services configured with NodePort for host machine access:
- **MinIO**: NodePort 30900 (API), 30901 (Console)
- **PostgreSQL**: NodePort 30432
- **Hive Metastore**: NodePort 30083
- **Spark Master**: NodePort 30082 (UI), 30077 (Master)
- **Spark Worker**: NodePort 30084
- **Trino**: NodePort 30081

### ConfigMaps (`configmap.yaml`)
- **trino-config**: Trino configuration files
  - config.properties
  - jvm.config
  - node.properties
  - hive.properties (Hive connector)
  - iceberg.properties (Iceberg connector)
- **hadoop-config**: Hadoop configuration for S3 access
  - core-site.xml

### Jobs (`job-minio-init.yaml`)
- **minio-init**: One-time job to create required buckets
  - Creates: `lakehouse`, `warehouse`, `iceberg` buckets
  - Runs after MinIO is ready

## Comparison: Docker Compose vs Kubernetes

| Docker Compose Service | Kubernetes Resource | NodePort | Notes |
|------------------------|---------------------|----------|-------|
| minio | Deployment + Service | 30900, 30901 | Ready |
| minio-init | Job | N/A | Runs once |
| postgres | Deployment + Service | 30432 | Ready |
| hive-metastore | Deployment + Service | 30083 | Uses init containers for dependencies |
| spark-master | Deployment + Service | 30082, 30077 | Ready |
| spark-worker | Deployment + Service | 30084 | Ready |
| trino | Deployment + Service | 30081 | Ready |

## Key Differences from Docker Compose

### Volume Management
- **Docker**: Named volumes (`minio-data`, `postgres-data`)
- **Kubernetes**: EmptyDir volumes (data is ephemeral)
- **Production**: Should use PersistentVolumeClaims (PVCs)

### Networking
- **Docker**: Bridge network (`lakehouse-network`)
- **Kubernetes**: Service DNS (e.g., `minio:9000`, `postgres:5432`)

### Health Checks
- **Docker**: `healthcheck` directives
- **Kubernetes**: `livenessProbe` and `readinessProbe`

### Initialization
- **Docker**: `depends_on` with conditions
- **Kubernetes**: Init containers and Jobs

### Configuration
- **Docker**: Volume mounts from host directories
- **Kubernetes**: ConfigMaps mounted as volumes

## Deployment Commands

### Deploy All Resources
```bash
./deploy.sh
```

Or manually:
```bash
kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f job-minio-init.yaml
```

### Clean Up All Resources
```bash
./cleanup.sh
```

Or manually:
```bash
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f job-minio-init.yaml
kubectl delete -f configmap.yaml
```

## Quick Start

1. **Deploy the stack:**
   ```bash
   cd k8s
   ./deploy.sh
   ```

2. **Verify all services are running:**
   ```bash
   kubectl get pods
   kubectl get svc
   ```

3. **Access services** (see `ACCESS_GUIDE.md` for details):
   - MinIO Console: http://localhost:30901
   - Trino UI: http://localhost:30081
   - Spark Master: http://localhost:30082

4. **Test Trino connection:**
   ```bash
   trino --server localhost:30081
   ```

## Production Considerations

This setup is designed for **development and testing**. For production:

### 1. Persistent Storage
Replace `emptyDir` with `PersistentVolumeClaim`:
```yaml
volumes:
  - name: minio-data
    persistentVolumeClaim:
      claimName: minio-pvc
```

### 2. Secrets Management
Replace plain-text credentials with Kubernetes Secrets:
```yaml
env:
  - name: POSTGRES_PASSWORD
    valueFrom:
      secretKeyRef:
        name: postgres-secret
        key: password
```

### 3. Resource Limits
Adjust CPU/memory based on workload:
```yaml
resources:
  requests:
    memory: "4Gi"
    cpu: "2000m"
  limits:
    memory: "8Gi"
    cpu: "4000m"
```

### 4. High Availability
Increase replicas for critical services:
```yaml
spec:
  replicas: 3
```

### 5. Service Exposure
For production, use:
- **LoadBalancer** type for cloud environments
- **Ingress** with TLS for HTTPS
- **Service Mesh** for advanced networking

### 6. Monitoring
Add:
- Prometheus metrics exporters
- Grafana dashboards
- Log aggregation (ELK/EFK stack)

## File Structure

```
k8s/
├── ACCESS_GUIDE.md          # Detailed access documentation
├── DEPLOYMENT_SUMMARY.md    # This file
├── README.md                # General overview
├── configmap.yaml           # Configuration data
├── deployment.yaml          # All deployments
├── service.yaml             # All services with NodePort
├── job-minio-init.yaml      # MinIO bucket initialization
├── deploy.sh                # Deployment script
└── cleanup.sh               # Cleanup script
```

## Troubleshooting

### Pods not starting
```bash
kubectl describe pod <pod-name>
kubectl logs <pod-name>
```

### Service not accessible
```bash
kubectl get svc
kubectl get endpoints <service-name>
```

### Hive Metastore taking too long
This is normal. The hive-metastore deployment can take 90-120 seconds to initialize.

### MinIO buckets not created
Check the minio-init job:
```bash
kubectl get jobs
kubectl logs job/minio-init
```

Re-run if needed:
```bash
kubectl delete job minio-init
kubectl apply -f job-minio-init.yaml
```

## Next Steps

1. Review `ACCESS_GUIDE.md` for detailed connection examples
2. Test connectivity to all services
3. Run sample Spark/Trino queries
4. Consider implementing production improvements listed above
