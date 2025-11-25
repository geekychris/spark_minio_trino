# Quick Reference Card

## Setup & Startup

```bash
# One-time setup and start
./setup_lakehouse.sh

# Or manually:
docker-compose up -d
```

## Generate Sample Data

```bash
# Generate e-commerce data (customers, products, orders)
./run_spark_app.sh
```

## Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| MinIO Console | http://localhost:9001 | admin / password123 |
| Spark Master UI | http://localhost:8082 | - |
| Trino Web UI | http://localhost:8081 | - |

## Trino CLI

```bash
# Start Trino CLI
docker exec -it trino trino

# Inside Trino:
SHOW CATALOGS;
USE lakehouse.ecommerce;
SHOW TABLES;
SELECT * FROM customers LIMIT 10;
```

## Useful SQL Queries

```sql
-- Count records in each table
SELECT COUNT(*) FROM customers;  -- 1,000
SELECT COUNT(*) FROM products;   -- 100
SELECT COUNT(*) FROM orders;     -- 5,000

-- Top customers by spending
SELECT 
    c.first_name || ' ' || c.last_name as customer,
    COUNT(o.order_id) as orders,
    ROUND(SUM(o.total_amount), 2) as total_spent
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name
ORDER BY total_spent DESC
LIMIT 10;

-- Revenue by product category
SELECT 
    p.category,
    COUNT(o.order_id) as order_count,
    ROUND(SUM(o.total_amount), 2) as revenue
FROM products p
JOIN orders o ON p.product_id = o.product_id
GROUP BY p.category
ORDER BY revenue DESC;

-- Orders by status
SELECT status, COUNT(*) as count
FROM orders
GROUP BY status;
```

## Spark Operations

```bash
# Access Spark master container
docker exec -it spark-master bash

# Run custom PySpark script
docker exec -it spark-master spark-submit \
  --master spark://spark-master:7077 \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3 \
  /opt/spark-apps/your_script.py

# Start PySpark shell
docker exec -it spark-master pyspark \
  --master spark://spark-master:7077 \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3
```

## MinIO Operations

```bash
# Access MinIO client
docker exec -it minio-init mc alias set local http://minio:9000 admin password123

# List buckets
docker exec -it minio-init mc ls local/

# List files in warehouse bucket
docker exec -it minio-init mc ls local/warehouse/

# Copy file from MinIO
docker exec -it minio-init mc cp local/warehouse/path/to/file /tmp/
```

## Monitoring & Logs

```bash
# View all container status
docker-compose ps

# View logs for a service
docker-compose logs -f spark-master
docker-compose logs -f trino
docker-compose logs -f minio

# View Spark application logs
docker exec -it spark-master ls /opt/spark/work
```

## Troubleshooting

```bash
# Restart a service
docker-compose restart spark-master
docker-compose restart trino

# Check MinIO health
curl http://localhost:9000/minio/health/live

# Verify Iceberg catalog config
docker exec -it trino cat /etc/trino/catalog/iceberg.properties

# Check if data exists in MinIO
docker exec -it minio-init mc ls local/warehouse/ecommerce/
```

## Cleanup

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes all data!)
docker-compose down -v

# Remove local directories
rm -rf spark-data/
```

## Advanced: Time Travel with Iceberg

```sql
-- View table history
SELECT * FROM lakehouse.ecommerce.orders$history;

-- View snapshots
SELECT * FROM lakehouse.ecommerce.orders$snapshots;

-- Query historical data (replace with actual snapshot_id)
SELECT * FROM lakehouse.ecommerce.orders 
FOR VERSION AS OF 1234567890123456789;
```

## Advanced: Schema Evolution

```sql
-- Add column (run in Spark/Trino)
ALTER TABLE lakehouse.ecommerce.customers 
ADD COLUMN loyalty_points INTEGER;

-- Rename column
ALTER TABLE lakehouse.ecommerce.customers 
RENAME COLUMN email TO email_address;

-- Drop column
ALTER TABLE lakehouse.ecommerce.customers 
DROP COLUMN loyalty_points;
```

## Container Management

```bash
# View resource usage
docker stats

# Access container shell
docker exec -it trino bash
docker exec -it spark-master bash
docker exec -it minio bash

# View network
docker network ls
docker network inspect trino_docker_lakehouse-network
```
