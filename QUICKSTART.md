# Lakehouse Demo - Quick Start Guide

## Architecture Overview

This demo implements a modern data lakehouse architecture with the following components:

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

- **MinIO**: S3-compatible object storage (data lake storage layer)
- **Postgres**: Metadata database for Hive Metastore
- **Hive Metastore**: Centralized metadata repository for table definitions
- **Spark**: Distributed data processing engine for ETL and analytics
- **Kafka + Zookeeper**: Event streaming platform for real-time data
- **Trino**: Distributed SQL query engine for interactive analytics

### Data Flow

1. Spark generates sample e-commerce data (customers, products, orders)
2. Data is stored in Parquet format in MinIO (S3-compatible storage)
3. Metadata is registered in Hive Metastore
4. Trino provides SQL interface to query the data

## Quick Start

### Prerequisites
- Docker and Docker Compose installed
- At least 4GB of available memory

### Start Everything

Run the automated startup script:
```bash
chmod +x start_lakehouse.sh
./start_lakehouse.sh
```

This script will:
1. Start all infrastructure services (MinIO, Postgres, Hive Metastore)
2. Initialize storage buckets
3. Start Spark cluster
4. Start Kafka and Trino
5. Generate sample e-commerce data

### Query Data with Trino

Connect to Trino CLI:
```bash
docker exec -it trino trino
```

Run queries:
```sql
-- List available schemas
SHOW SCHEMAS IN hive;

-- Use the ecommerce schema
USE hive.ecommerce;

-- Show tables
SHOW TABLES;

-- Query customers
SELECT * FROM customers LIMIT 10;

-- Query orders with analytics
SELECT 
    status, 
    COUNT(*) as order_count,
    SUM(total_amount) as total_revenue
FROM orders
GROUP BY status
ORDER BY total_revenue DESC;

-- Exit
quit;
```

## Service Access

| Service | URL | Credentials |
|---------|-----|-------------|
| MinIO Console | http://localhost:9001 | admin / password123 |
| Spark UI | http://localhost:8082 | N/A |
| Trino | http://localhost:8081 | N/A |

## Common Commands

**View running containers:**
```bash
docker compose ps
```

**Stop all services:**
```bash
docker compose down
```

**View logs:**
```bash
docker compose logs -f [service-name]
```

**Restart a service:**
```bash
docker compose restart [service-name]
```

## Sample Dataset

The demo generates an e-commerce dataset with:
- **1,000 customers** (name, email, location)
- **100 products** (name, category, price)
- **5,000 orders** (customer, product, quantity, status, date)

## Troubleshooting

**Services not starting:** Ensure you have enough memory allocated to Docker (4GB minimum).

**Port conflicts:** Check if ports 8081, 8082, 9000, 9001, 9083, 9092 are available.

**Data not appearing:** Wait 30 seconds after startup for Hive Metastore to fully initialize.
