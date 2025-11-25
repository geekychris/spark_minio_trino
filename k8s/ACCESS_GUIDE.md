# Kubernetes Services Access Guide

This guide explains how to access all services running in Kubernetes from your host machine using NodePort.

## Service Access URLs

All services are accessible from your host machine using `localhost` and the assigned NodePort.

### MinIO (Object Storage)
- **API Endpoint**: `http://localhost:30900`
- **Console (Web UI)**: `http://localhost:30901`
- **Credentials**: 
  - Username: `admin`
  - Password: `password123`
- **Buckets**: `lakehouse`, `warehouse`, `iceberg`

### PostgreSQL (Hive Metastore Database)
- **Host**: `localhost`
- **Port**: `30432`
- **Database**: `metastore`
- **Username**: `hive`
- **Password**: `hive`
- **Connection String**: `postgresql://hive:hive@localhost:30432/metastore`

### Hive Metastore (Thrift)
- **Host**: `localhost`
- **Port**: `30083`
- **Protocol**: Thrift
- **URI**: `thrift://localhost:30083`

### Spark Master
- **Web UI**: `http://localhost:30082`
- **Master URL**: `spark://localhost:30077`
- **Description**: Spark cluster management interface

### Spark Worker
- **Web UI**: `http://localhost:30084`
- **Description**: Individual worker node interface

### Trino (Query Engine)
- **HTTP Endpoint**: `http://localhost:30081`
- **Web UI**: `http://localhost:30081`
- **CLI Connection**: `trino --server localhost:30081 --catalog iceberg --schema default`

## Quick Reference Table

| Service | Type | Port | NodePort | Purpose |
|---------|------|------|----------|---------|
| MinIO API | HTTP | 9000 | 30900 | S3-compatible storage API |
| MinIO Console | HTTP | 9001 | 30901 | MinIO web interface |
| PostgreSQL | TCP | 5432 | 30432 | Metastore database |
| Hive Metastore | Thrift | 9083 | 30083 | Table metadata service |
| Spark Master Web | HTTP | 8080 | 30082 | Spark UI |
| Spark Master | TCP | 7077 | 30077 | Spark cluster coordination |
| Spark Worker | HTTP | 8081 | 30084 | Worker node UI |
| Trino | HTTP | 8080 | 30081 | Query engine interface |

## Testing Connectivity

### Test MinIO
```bash
# Check MinIO health
curl http://localhost:30900/minio/health/live

# Access MinIO console in browser
open http://localhost:30901
```

### Test PostgreSQL
```bash
# Using psql
psql -h localhost -p 30432 -U hive -d metastore

# Using Docker (if psql not installed locally)
docker run --rm -it postgres:15 psql -h host.docker.internal -p 30432 -U hive -d metastore
```

### Test Hive Metastore
```bash
# Check if port is listening
nc -zv localhost 30083
```

### Test Spark Master
```bash
# Check Spark Master UI
curl -I http://localhost:30082

# Open in browser
open http://localhost:30082
```

### Test Trino
```bash
# Check Trino info endpoint
curl http://localhost:30081/v1/info

# Access Trino UI
open http://localhost:30081

# Connect with Trino CLI (if installed)
trino --server localhost:30081
```

## Using Services in Applications

### Python Example (Using MinIO)
```python
from minio import Minio

client = Minio(
    "localhost:30900",
    access_key="admin",
    secret_key="password123",
    secure=False
)

# List buckets
buckets = client.list_buckets()
for bucket in buckets:
    print(bucket.name)
```

### Python Example (Using PostgreSQL)
```python
import psycopg2

conn = psycopg2.connect(
    host="localhost",
    port=30432,
    database="metastore",
    user="hive",
    password="hive"
)

cursor = conn.cursor()
cursor.execute("SELECT version();")
print(cursor.fetchone())
```

### Python Example (Using Trino)
```python
from trino.dbapi import connect

conn = connect(
    host="localhost",
    port=30081,
    user="admin",
    catalog="iceberg",
    schema="default"
)

cursor = conn.cursor()
cursor.execute("SHOW SCHEMAS")
print(cursor.fetchall())
```

### Spark Example (Connecting to Spark Master)
```bash
spark-submit \
  --master spark://localhost:30077 \
  --deploy-mode client \
  your_spark_app.py
```

## Port Forwarding Alternative

If NodePort doesn't work in your environment, you can use kubectl port-forward:

```bash
# Forward MinIO API
kubectl port-forward svc/minio 9000:9000

# Forward MinIO Console
kubectl port-forward svc/minio 9001:9001

# Forward PostgreSQL
kubectl port-forward svc/postgres 5432:5432

# Forward Hive Metastore
kubectl port-forward svc/hive-metastore 9083:9083

# Forward Spark Master
kubectl port-forward svc/spark-master 8080:8080 7077:7077

# Forward Trino
kubectl port-forward svc/trino 8080:8080
```

## Notes

- **NodePort Range**: Kubernetes NodePorts are typically in the range 30000-32767
- **Security**: These configurations use default credentials and are suitable for development only
- **Firewall**: Ensure your local firewall allows connections to these ports
- **Minikube Users**: Use `minikube service <service-name>` or access via minikube IP
- **Docker Desktop**: NodePort should work directly with `localhost`
- **Kind/K3s**: May require additional configuration or port mapping

## Troubleshooting

### Can't connect to NodePort
1. Check if service is running: `kubectl get svc`
2. Check if pods are ready: `kubectl get pods`
3. For Minikube: Get cluster IP with `minikube ip` and use that instead of localhost
4. Check pod logs: `kubectl logs <pod-name>`

### Connection refused
- Ensure the pod is in Running state
- Check if the service selector matches pod labels
- Verify readiness probes are passing

### Minikube specific issues
```bash
# Get the URL for a service
minikube service <service-name> --url

# Example for Trino
minikube service trino --url
```
