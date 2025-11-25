# Kubernetes Architecture Overview

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Host Machine                                 │
│                                                                      │
│  Browser/CLI Access via NodePort:                                   │
│  • MinIO Console:  localhost:30901                                  │
│  • MinIO API:      localhost:30900                                  │
│  • PostgreSQL:     localhost:30432                                  │
│  • Hive Metastore: localhost:30083                                  │
│  • Spark Master:   localhost:30082 (UI), localhost:30077 (Master)  │
│  • Spark Worker:   localhost:30084                                  │
│  • Trino:          localhost:30081                                  │
│                                                                      │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               │ NodePort Services
                               │
┌──────────────────────────────▼───────────────────────────────────────┐
│                    Kubernetes Cluster                                │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                     Services Layer                          │   │
│  │                                                              │   │
│  │  minio:9000/9001  postgres:5432  hive-metastore:9083       │   │
│  │  spark-master:8080/7077  spark-worker:8081  trino:8080     │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                   │
│  ┌──────────────────────────────▼───────────────────────────────┐   │
│  │                      Pods Layer                              │   │
│  │                                                              │   │
│  │  ┌─────────┐  ┌──────────┐  ┌───────────────┐             │   │
│  │  │  MinIO  │  │PostgreSQL│  │Hive Metastore │             │   │
│  │  │         │  │          │  │               │             │   │
│  │  │ Port:   │  │ Port:    │  │ Port: 9083    │             │   │
│  │  │ 9000    │  │ 5432     │  │               │             │   │
│  │  │ 9001    │  │          │  │               │             │   │
│  │  └────┬────┘  └─────┬────┘  └───────┬───────┘             │   │
│  │       │             │               │                      │   │
│  │  ┌────┴─────────────┴───────────────┴────────┐            │   │
│  │  │         Storage & Metadata Layer          │            │   │
│  │  │                                            │            │   │
│  │  │  • Object Storage (S3-compatible)         │            │   │
│  │  │  • Relational Database (Metastore)        │            │   │
│  │  │  • Table Metadata (Hive/Iceberg)          │            │   │
│  │  └────────────────────────────────────────────┘            │   │
│  │                                                              │   │
│  │  ┌──────────────┐       ┌──────────────┐                   │   │
│  │  │ Spark Master │       │ Spark Worker │                   │   │
│  │  │              │       │              │                   │   │
│  │  │ Port: 8080   │◄─────►│ Port: 8081   │                   │   │
│  │  │ Port: 7077   │       │              │                   │   │
│  │  └──────────────┘       └──────────────┘                   │   │
│  │                                                              │   │
│  │  ┌──────────────────────────────────────┐                  │   │
│  │  │              Trino                   │                  │   │
│  │  │                                       │                  │   │
│  │  │  Port: 8080 (HTTP/UI)                │                  │   │
│  │  │                                       │                  │   │
│  │  │  Catalogs:                            │                  │   │
│  │  │  • hive (Hive connector)              │                  │   │
│  │  │  • iceberg (Iceberg connector)        │                  │   │
│  │  └──────────────────────────────────────┘                  │   │
│  │                                                              │   │
│  │  ┌──────────────────────────────────────┐                  │   │
│  │  │       Job: minio-init                │                  │   │
│  │  │  (Creates buckets, runs once)        │                  │   │
│  │  └──────────────────────────────────────┘                  │   │
│  │                                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                     ConfigMaps                               │   │
│  │                                                              │   │
│  │  • trino-config (Trino configuration)                       │   │
│  │  • hadoop-config (Hadoop/S3 configuration)                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

## Component Communication Flow

### 1. Data Storage Layer
```
MinIO (Object Storage)
   ↕
PostgreSQL (Metadata Database)
   ↕
Hive Metastore (Metadata Service)
```

### 2. Compute Layer
```
Trino Query Engine
   ↓
   ├─→ Hive Connector → Hive Metastore → MinIO
   └─→ Iceberg Connector → Hive Metastore → MinIO

Spark Cluster
   ├─→ Spark Master (Coordinator)
   └─→ Spark Worker (Executor)
        ↓
        Hive Metastore → MinIO
```

### 3. Data Flow Example (Query Execution)
```
1. User → Trino (localhost:30081)
2. Trino → Hive Metastore (Get table metadata)
3. Hive Metastore → PostgreSQL (Query metadata)
4. Trino → MinIO (Read actual data files)
5. Trino → User (Return results)
```

## Port Mapping Reference

### Internal Cluster Ports (DNS Names)
These are used for **inter-pod communication** within the Kubernetes cluster:

| Service | DNS Name | Port | Protocol | Purpose |
|---------|----------|------|----------|---------|
| MinIO API | minio | 9000 | HTTP | S3 API |
| MinIO Console | minio | 9001 | HTTP | Web UI |
| PostgreSQL | postgres | 5432 | TCP | Database |
| Hive Metastore | hive-metastore | 9083 | Thrift | Metadata |
| Spark Master Web | spark-master | 8080 | HTTP | UI |
| Spark Master | spark-master | 7077 | TCP | Cluster |
| Spark Worker | spark-worker | 8081 | HTTP | UI |
| Trino | trino | 8080 | HTTP | Query Engine |

### External NodePorts (Host Access)
These are used for **host machine access** from outside the cluster:

| Service | Host Address | NodePort | Container Port |
|---------|--------------|----------|----------------|
| MinIO API | localhost:30900 | 30900 | 9000 |
| MinIO Console | localhost:30901 | 30901 | 9001 |
| PostgreSQL | localhost:30432 | 30432 | 5432 |
| Hive Metastore | localhost:30083 | 30083 | 9083 |
| Spark Master Web | localhost:30082 | 30082 | 8080 |
| Spark Master | localhost:30077 | 30077 | 7077 |
| Spark Worker | localhost:30084 | 30084 | 8081 |
| Trino | localhost:30081 | 30081 | 8080 |

## Resource Dependencies

### Startup Order
```
1. ConfigMaps (Configuration)
   ↓
2. MinIO (Object Storage)
   ↓
3. MinIO Init Job (Create Buckets)
   ↓
4. PostgreSQL (Database)
   ↓
5. Hive Metastore (wait for PostgreSQL + MinIO)
   ↓
6. Spark Master & Worker
   ↓
7. Trino (wait for Hive Metastore + MinIO)
```

### Runtime Dependencies
```
Trino depends on:
  • Hive Metastore (metadata)
  • MinIO (data storage)

Hive Metastore depends on:
  • PostgreSQL (metadata persistence)
  • MinIO (data access)

Spark Worker depends on:
  • Spark Master (cluster coordination)
```

## Security Considerations

### Current Configuration (Development)
- Plain-text credentials in ConfigMaps/Deployments
- No TLS/SSL encryption
- Default passwords
- No authentication between services
- No network policies

### Production Recommendations
1. **Use Kubernetes Secrets** for sensitive data
2. **Enable TLS** for all HTTP/Thrift communications
3. **Implement authentication** (LDAP/OAuth)
4. **Network Policies** to restrict pod-to-pod traffic
5. **RBAC** for Kubernetes API access
6. **Pod Security Policies** to restrict capabilities
7. **Rotate credentials** regularly
8. **Use external secret management** (Vault, AWS Secrets Manager)

## Scalability Options

### Horizontal Scaling
```yaml
# Scale Spark Workers
kubectl scale deployment spark-worker --replicas=3

# Scale Trino (requires additional configuration)
kubectl scale deployment trino --replicas=3
```

### Vertical Scaling
Adjust resource requests/limits in `deployment.yaml`:
```yaml
resources:
  requests:
    memory: "8Gi"
    cpu: "4000m"
  limits:
    memory: "16Gi"
    cpu: "8000m"
```

## Monitoring Endpoints

Each service exposes metrics/status endpoints:

| Service | Endpoint | Purpose |
|---------|----------|---------|
| MinIO | http://localhost:30900/minio/health/live | Health check |
| Trino | http://localhost:30081/v1/info | Cluster info |
| Trino | http://localhost:30081/v1/query | Query monitoring |
| Spark Master | http://localhost:30082 | Cluster status |
| Spark Worker | http://localhost:30084 | Worker status |

## Volume Mounts

### Current Setup (Ephemeral)
- All volumes use `emptyDir` (data lost on pod restart)
- Suitable for development/testing only

### Production Setup (Persistent)
Should use PersistentVolumeClaims:
- MinIO: Store data objects
- PostgreSQL: Store metadata database
- Spark: Store application jars and data

## Next Steps

1. Review `ACCESS_GUIDE.md` for connection examples
2. Check `DEPLOYMENT_SUMMARY.md` for deployment details
3. Deploy using `./deploy.sh`
4. Test connectivity to all services
5. Run sample queries through Trino
6. Consider production enhancements
