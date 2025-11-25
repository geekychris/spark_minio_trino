# Trino Docker and Kubernetes Setup

This repository contains Docker and Kubernetes resources for deploying Trino with REST API and UI access.

## 🚀 NEW: Lakehouse Demo

A complete lakehouse demo using **MinIO** (S3-compatible storage), **Apache Spark**, **Apache Iceberg**, and **Trino**!

**Quick start:**
```bash
./setup_lakehouse.sh
./run_spark_app.sh
```

**See [LAKEHOUSE_DEMO.md](LAKEHOUSE_DEMO.md) for full documentation.**

## Quick Start

### Docker Compose

```bash
# Start Trino
docker-compose up -d

# Access the UI
open http://localhost:8081
```

### Kubernetes

#### Deploy with individual manifests:

```bash
# Apply all resources
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Optional: Apply ingress (requires ingress controller)
kubectl apply -f k8s/ingress.yaml
```

#### Deploy with all-in-one manifest:

```bash
kubectl apply -f k8s/trino-all-in-one.yaml
```

#### Access Trino:

```bash
# Get service info
kubectl get svc trino

# Access via NodePort (default: 30080)
# For minikube:
minikube service trino

# For other clusters, get node IP:
kubectl get nodes -o wide
# Then access: http://<NODE_IP>:30080

# Alternative: Port forward for local access
kubectl port-forward svc/trino 8080:8080
# Then access: http://localhost:8080
```

## Endpoints

**Docker Compose:**
- **Web UI**: `http://localhost:8081/ui/`
- **REST API**: `http://localhost:8081/v1/`
- **Info endpoint**: `http://localhost:8081/v1/info`

**Kubernetes (NodePort):**
- **Web UI**: `http://localhost:30080/ui/`
- **REST API**: `http://localhost:30080/v1/`
- **Info endpoint**: `http://localhost:30080/v1/info`

## Testing REST API

```bash
# Get server info
curl http://localhost:8080/v1/info

# Execute a query
curl -X POST http://localhost:8080/v1/statement \
  -H "X-Trino-User: admin" \
  -d "SELECT 1"
```

## Configuration

To add custom configurations or catalogs:

1. Create `config/` directory with your `config.properties`
2. Create `catalog/` directory with catalog property files
3. Mount them in docker-compose or create ConfigMaps for Kubernetes

## Scaling

To add worker nodes in Kubernetes:

```bash
kubectl scale deployment trino --replicas=3
```

Note: You'll need to configure coordinator/worker roles separately for a production cluster.
