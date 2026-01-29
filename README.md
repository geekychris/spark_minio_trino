# Data Lakehouse Demo

## TL;DR

**Purpose:** Complete data lakehouse environment with object storage, distributed processing, and SQL analytics.

**Start everything:**
```bash
./start_lakehouse.sh
```

**Stop everything:**
```bash
./stop_lakehouse.sh          # Keeps data
./stop_lakehouse.sh --clean  # Removes data
```

**Query data:**
```bash
docker exec -it trino trino
trino> USE hive.ecommerce;
trino> SELECT * FROM customers LIMIT 10;
```

**Access UIs:**
- MinIO Console: http://localhost:9001 (admin/password123)
- Spark UI: http://localhost:8082
- Trino UI: http://localhost:8081

---

## Architecture

Modern data lakehouse with S3-compatible storage, distributed processing, and SQL query engine:

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Kafka     │────▶│    Spark     │────▶│   MinIO     │
│ (Streaming) │     │  (Processing)│     │ (S3 Storage)│
└─────────────┘     └──────────────┘     └─────────────┘
                            │                     │
                            ▼                     ▼
                    ┌──────────────┐     ┌─────────────┐
                    │     Hive     │────▶│  Postgres   │
                    │  Metastore   │     │ (Metadata)  │
                    └──────────────┘     └─────────────┘
                            │
                            ▼
                    ┌──────────────┐
                    │    Trino     │
                    │  (SQL Query) │
                    └──────────────┘
```

### Components

- **MinIO**: S3-compatible object storage (data lake)
- **Postgres**: Metadata database for Hive Metastore
- **Hive Metastore**: Centralized metadata repository for tables
- **Spark**: Distributed data processing and ETL
- **Kafka + Zookeeper**: Event streaming platform
- **Trino**: Distributed SQL query engine for analytics

## Quick Start

### Automated Setup (Recommended)

```bash
# Start everything and generate sample data
./start_lakehouse.sh

# Stop when done (keeps data)
./stop_lakehouse.sh

# Stop and remove all data
./stop_lakehouse.sh --clean
```

The automated script will:
1. Start all infrastructure services in correct order
2. Initialize MinIO buckets
3. Generate sample e-commerce data (1K customers, 100 products, 5K orders)
4. Display all service URLs and ports

### Generate Additional Data

After services are running, you can run data generators:

**Parquet format (default):**
```bash
./run_spark_app.sh              # Python version
./run_spark_app_java.sh         # Java version (builds with Maven)
```

**Iceberg format:**
```bash
./run_iceberg_spark_app.sh      # Python version
./run_iceberg_spark_app_java.sh # Java version (builds with Maven)
```

### Query Data with Trino

```bash
# Interactive shell
docker exec -it trino trino

# Once in Trino shell:
USE hive.ecommerce;
SHOW TABLES;

# Sample queries
SELECT * FROM customers LIMIT 10;
SELECT * FROM products WHERE category='Electronics' LIMIT 5;
SELECT * FROM orders WHERE status='delivered' LIMIT 10;

# Analytics queries
SELECT COUNT(*) FROM customers;
SELECT category, COUNT(*) as product_count FROM products GROUP BY category;
SELECT status, COUNT(*) as order_count, SUM(total_amount) as total_revenue 
FROM orders GROUP BY status;
```

## Accessing Layers

### Web Interfaces

| Service | URL | Purpose | Credentials |
|---------|-----|---------|-------------|
| **MinIO Console** | http://localhost:9001 | Browse S3 buckets, upload/download files | admin / password123 |
| **Spark Master UI** | http://localhost:8082 | Monitor Spark cluster, running jobs, workers | N/A |
| **Trino Web UI** | http://localhost:8081 | View query history, cluster status, workers | N/A |

### Service Ports

| Service | Port | Purpose |
|---------|------|----------|
| Trino | 8081 | SQL queries |
| Spark Master | 7077 | Spark cluster coordination |
| Hive Metastore | 9083 | Table metadata |
| MinIO S3 API | 9000 | Object storage API |
| Kafka | 9092 | Event streaming |
| Zookeeper | 2181 | Kafka coordination |
| PostgreSQL | 5432 | Metadata database |

### Command-Line Access

**Trino CLI:**
```bash
docker exec -it trino trino
```

**MinIO CLI:**
```bash
# List buckets
docker exec minio mc ls myminio/

# Browse data files
docker exec minio mc ls myminio/warehouse/ --recursive
```

**Spark Shell:**
```bash
# PySpark
docker exec -it spark-master /opt/spark/bin/pyspark

# Scala Spark
docker exec -it spark-master /opt/spark/bin/spark-shell
```

**Kafka:**
```bash
# List topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consume messages
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic <topic-name> --from-beginning
```


## Architecture Details

### Storage Format
- **Parquet**: Columnar storage format optimized for analytics
- **Compression**: Snappy compression (default)
- **Partitioning**: Orders table partitioned by `status` field

### Metastore
- **Spark**: Uses embedded Derby metastore for internal table tracking
- **Trino**: Uses file-based Hive metastore with external table definitions

### Data Flow
1. Spark generates data and writes Parquet files to MinIO (s3a://warehouse/)
2. Files organized in Hive-style directory structure (database.db/table/)
3. Trino creates external table definitions pointing to the Parquet files
4. Trino queries read directly from Parquet files in MinIO

## Example Queries

```sql
-- Customers by state
SELECT state, COUNT(*) as customer_count 
FROM customers 
GROUP BY state 
ORDER BY customer_count DESC;

-- Products by category with avg price
SELECT category, COUNT(*) as product_count, AVG(price) as avg_price
FROM products
GROUP BY category
ORDER BY avg_price DESC;

-- Orders summary by status
SELECT status, 
       COUNT(*) as order_count,
       SUM(total_amount) as total_revenue,
       AVG(total_amount) as avg_order_value
FROM orders
GROUP BY status;

-- Top customers (requires joining orders with customers)
-- Note: Join queries work across tables stored in MinIO
SELECT c.first_name, c.last_name, c.city,
       COUNT(o.order_id) as order_count,
       SUM(o.total_amount) as total_spent
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name, c.city
ORDER BY total_spent DESC
LIMIT 10;
```

## Sample Data

The demo generates an e-commerce dataset:
- **1,000 customers** (name, email, city, state)
- **100 products** (name, category, price)
- **5,000 orders** (customer, product, quantity, status, date)

Data is stored in Parquet format in MinIO at `s3a://warehouse/ecommerce.db/`

## Troubleshooting

**Services not starting:**
- Ensure Docker has at least 4GB memory allocated
- Check for port conflicts: 8081, 8082, 9000, 9001, 9083, 9092, 5432, 2181

**Trino connection errors:**
- Wait 30-90 seconds for Hive Metastore to initialize
- Check service health: `docker compose ps`

**Data not appearing:**
- Verify Hive Metastore is healthy: `docker ps | grep hive-metastore`
- Check MinIO has data: `docker exec minio mc ls myminio/warehouse/ --recursive`

**View logs:**
```bash
docker compose logs -f [service-name]
```
