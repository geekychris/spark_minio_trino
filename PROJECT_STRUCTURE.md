# Project Structure

## Overview

This project contains a complete lakehouse demo using MinIO, Apache Spark, Apache Iceberg, and Trino.

## Directory Structure

```
trino_docker/
├── docker-compose.yml          # Main orchestration file for all services
├── Dockerfile                  # Custom Trino image (if needed)
│
├── catalog/                    # Trino catalog configurations
│   ├── iceberg.properties      # Iceberg catalog config (points to MinIO)
│   └── tpch.properties         # TPC-H sample data catalog
│
├── config/                     # Trino server configuration
│   ├── config.properties       # Main Trino config
│   ├── node.properties         # Node-specific config
│   └── jvm.config             # JVM settings
│
├── spark-apps/                 # PySpark applications
│   └── generate_data.py        # Data generator for e-commerce dataset
│
├── spark-data/                 # Local Spark temporary data (auto-created)
│
├── k8s/                        # Kubernetes manifests (original setup)
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   ├── ingress.yaml
│   └── trino-all-in-one.yaml
│
├── setup_lakehouse.sh          # Automated setup script
├── run_spark_app.sh            # Run Spark data generation
│
├── GETTING_STARTED.md          # Quick start guide (START HERE!)
├── LAKEHOUSE_DEMO.md           # Complete documentation
├── QUICK_REFERENCE.md          # Command cheat sheet
├── README.md                   # Main README with overview
└── PROJECT_STRUCTURE.md        # This file
```

## Key Files

### Configuration Files

#### `docker-compose.yml`
Orchestrates all services:
- **minio**: S3-compatible object storage
- **minio-init**: Initialize MinIO buckets
- **spark-master**: Spark master node
- **spark-worker**: Spark worker node(s)
- **trino**: Distributed SQL query engine

#### `catalog/iceberg.properties`
Configures Trino to read Iceberg tables from MinIO:
```properties
connector.name=iceberg
iceberg.catalog.type=hadoop
iceberg.hadoop.catalog.warehouse=s3a://warehouse/
fs.native-s3.enabled=true
s3.endpoint=http://minio:9000
s3.path-style-access=true
s3.aws-access-key=admin
s3.aws-secret-key=password123
```

### Application Files

#### `spark-apps/generate_data.py`
PySpark application that:
- Creates e-commerce database schema
- Generates realistic customer, product, and order data
- Writes data to Iceberg tables in MinIO
- Displays sample data and statistics

#### `setup_lakehouse.sh`
Automated setup that:
- Checks Docker is running
- Creates necessary directories
- Starts all services with docker-compose
- Waits for services to be ready
- Displays access URLs

#### `run_spark_app.sh`
Convenience script to run Spark applications with correct configuration for Iceberg and MinIO.

### Documentation Files

#### `GETTING_STARTED.md` ⭐ START HERE
- Beginner-friendly introduction
- 3-step quick start
- Sample queries
- Web UI overview

#### `LAKEHOUSE_DEMO.md`
- Complete architecture overview
- Detailed setup instructions
- Advanced features (time travel, schema evolution)
- Troubleshooting guide

#### `QUICK_REFERENCE.md`
- Command cheat sheet
- Common SQL queries
- Container management
- Quick troubleshooting

#### `README.md`
- Main project overview
- Links to all documentation

## Services & Ports

| Service | Internal Port | External Port | Purpose |
|---------|--------------|---------------|---------|
| MinIO API | 9000 | 9000 | S3-compatible API |
| MinIO Console | 9001 | 9001 | Web UI for storage |
| Spark Master | 7077 | 7077 | Spark cluster coordination |
| Spark Web UI | 8080 | 8082 | Spark monitoring |
| Trino | 8080 | 8081 | SQL query interface |

## Data Flow

```
1. PySpark Script (generate_data.py)
   │
   ├─► Creates DataFrames with sample data
   │
   └─► Writes to Iceberg tables
       │
       └─► Stored in MinIO
           │
           ├─► Data files (Parquet)
           └─► Metadata (JSON)

2. Trino
   │
   ├─► Reads Iceberg metadata from MinIO
   │
   └─► Queries data files
       │
       └─► Returns SQL results
```

## Network Architecture

All services run on a Docker bridge network named `lakehouse-network`:

```
┌─────────────────────────────────────────┐
│      lakehouse-network (bridge)         │
│                                          │
│  ┌────────┐  ┌────────┐  ┌──────────┐ │
│  │ minio  │  │ spark  │  │  trino   │ │
│  │ :9000  │  │ :7077  │  │  :8080   │ │
│  └────────┘  └────────┘  └──────────┘ │
│                                          │
└─────────────────────────────────────────┘
          │
          │ Port mappings
          ▼
    Host machine
    (localhost)
```

## Volume Mounts

### Docker Volumes (Persistent)
- `minio-data`: Stores all object data

### Bind Mounts (Local directories)
- `./config` → `/etc/trino` (Trino config)
- `./catalog` → `/etc/trino/catalog` (Catalog configs)
- `./spark-apps` → `/opt/spark-apps` (Spark applications)
- `./spark-data` → `/opt/spark-data` (Spark temp data)

## Environment Variables

### MinIO
- `MINIO_ROOT_USER=admin`
- `MINIO_ROOT_PASSWORD=password123`

### Spark
- `SPARK_MODE=master` (or `worker`)
- `SPARK_MASTER_URL=spark://spark-master:7077`
- `SPARK_WORKER_MEMORY=2G`
- `SPARK_WORKER_CORES=2`

### Trino
- `TRINO_ENVIRONMENT=production`

## Technology Stack

### Core Components
- **Apache Iceberg 1.4.3**: Open table format
- **Apache Spark 3.5**: Data processing engine
- **Trino (latest)**: Distributed SQL query engine
- **MinIO (latest)**: Object storage

### Supporting Libraries
- **Hadoop AWS 3.3.4**: S3A filesystem
- **AWS SDK 2.20.18**: AWS client libraries

## Common Operations

### Start Everything
```bash
./setup_lakehouse.sh
```

### Generate Data
```bash
./run_spark_app.sh
```

### Query Data
```bash
docker exec -it trino trino
```

### View Logs
```bash
docker-compose logs -f [service-name]
```

### Stop Everything
```bash
docker-compose down
```

### Reset Everything
```bash
docker-compose down -v
rm -rf spark-data/
```

## Extending the Demo

### Add New Spark Applications
1. Create new `.py` file in `spark-apps/`
2. Use the same Spark session configuration as `generate_data.py`
3. Run with `./run_spark_app.sh your_app.py`

### Add New Catalogs to Trino
1. Create new `.properties` file in `catalog/`
2. Restart Trino: `docker-compose restart trino`
3. Verify: `docker exec -it trino trino` → `SHOW CATALOGS;`

### Scale Spark Workers
```bash
docker-compose up -d --scale spark-worker=3
```

### Change Storage Backend
Modify `catalog/iceberg.properties` to point to AWS S3, Azure Blob, or GCS instead of MinIO.

## Troubleshooting

See [LAKEHOUSE_DEMO.md](LAKEHOUSE_DEMO.md#troubleshooting) for detailed troubleshooting.

## Resources

- [Apache Iceberg](https://iceberg.apache.org/)
- [Apache Spark](https://spark.apache.org/)
- [Trino](https://trino.io/)
- [MinIO](https://min.io/)
