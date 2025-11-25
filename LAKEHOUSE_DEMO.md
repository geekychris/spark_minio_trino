# Lakehouse Demo with MinIO, Spark, and Iceberg

This demo showcases a complete lakehouse architecture using:
- **MinIO**: S3-compatible object storage
- **Apache Spark**: Data processing and generation
- **Apache Iceberg**: Open table format for lakehouse
- **Trino**: Distributed SQL query engine

## Architecture

```
┌─────────────────┐
│  Spark Master   │  Generate data using PySpark
│   & Worker      │  Write to Iceberg tables
└────────┬────────┘
         │
         │ Write Iceberg tables
         ▼
┌─────────────────┐
│     MinIO       │  S3-compatible storage
│  (Object Store) │  Stores Iceberg data & metadata
└────────┬────────┘
         │
         │ Read Iceberg tables
         ▼
┌─────────────────┐
│     Trino       │  Query Iceberg tables
│  (SQL Engine)   │  Run analytics
└─────────────────┘
```

## Quick Start

### 1. Start all services

```bash
docker-compose up -d
```

This will start:
- **MinIO**: Object storage (ports 9000, 9001)
- **Spark Master**: Port 8082
- **Spark Worker**: Connected to master
- **Trino**: SQL query engine (port 8081)

### 2. Wait for services to be ready

```bash
# Check all containers are running
docker-compose ps

# Wait about 30 seconds for Spark to fully initialize
```

### 3. Generate sample data

Run the Spark application to generate e-commerce data:

```bash
./run_spark_app.sh
```

This will create three Iceberg tables:
- `lakehouse.ecommerce.customers` (1,000 records)
- `lakehouse.ecommerce.products` (100 records)
- `lakehouse.ecommerce.orders` (5,000 records)

### 4. Query data with Trino

```bash
docker exec -it trino trino
```

Inside the Trino CLI:

```sql
-- Show available catalogs
SHOW CATALOGS;

-- Use the lakehouse catalog
USE lakehouse.ecommerce;

-- Show tables
SHOW TABLES;

-- Query customers
SELECT * FROM customers LIMIT 10;

-- Query products by category
SELECT category, COUNT(*) as product_count, 
       ROUND(AVG(price), 2) as avg_price
FROM products
GROUP BY category
ORDER BY product_count DESC;

-- Query orders with joins
SELECT 
    c.first_name || ' ' || c.last_name as customer_name,
    p.name as product_name,
    o.quantity,
    o.total_amount,
    o.status,
    o.order_date
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN products p ON o.product_id = p.product_id
WHERE o.status = 'delivered'
ORDER BY o.order_date DESC
LIMIT 20;

-- Revenue by state
SELECT 
    c.state,
    COUNT(DISTINCT o.customer_id) as customer_count,
    COUNT(o.order_id) as order_count,
    ROUND(SUM(o.total_amount), 2) as total_revenue
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.state
ORDER BY total_revenue DESC;
```

## Access Web Interfaces

- **MinIO Console**: http://localhost:9001
  - Username: `admin`
  - Password: `password123`
  - Browse buckets: `lakehouse` and `warehouse`

- **Spark Master UI**: http://localhost:8082
  - View cluster status and running applications

- **Trino Web UI**: http://localhost:8081
  - Monitor queries and cluster status

## Data Generation Details

The `generate_data.py` script creates realistic e-commerce data:

### Customers Table
- 1,000 customers
- Fields: customer_id, first_name, last_name, email, city, state
- Random names from predefined lists

### Products Table
- 100 products across 8 categories
- Fields: product_id, name, category, price
- Categories: Electronics, Clothing, Home & Garden, Sports, Books, Toys, Food, Health

### Orders Table
- 5,000 orders spanning Jan 2023 - Nov 2024
- Fields: order_id, customer_id, product_id, quantity, total_amount, status, order_date
- Partitioned by status (pending, shipped, delivered, cancelled)
- Random quantities (1-5) and realistic pricing

## Lakehouse Features Demonstrated

### 1. Schema Evolution
Iceberg supports adding, dropping, and renaming columns without rewriting data:

```sql
-- Example: Add a new column (in Spark)
ALTER TABLE lakehouse.ecommerce.customers 
ADD COLUMN loyalty_points INT;
```

### 2. Time Travel
Query data as it existed at a previous point in time:

```sql
-- View table history
SELECT * FROM lakehouse.ecommerce.orders$history;

-- Query from a specific snapshot
SELECT * FROM lakehouse.ecommerce.orders 
FOR VERSION AS OF <snapshot_id>;
```

### 3. Partitioning
The orders table is partitioned by status for efficient queries:

```sql
-- Efficient query using partition pruning
SELECT * FROM orders WHERE status = 'delivered';
```

### 4. ACID Transactions
All operations are ACID-compliant, ensuring data consistency.

## Advanced Operations

### Running Custom Spark Jobs

1. Place your PySpark script in `./spark-apps/`
2. Run it using:

```bash
docker exec -it spark-master spark-submit \
  --master spark://spark-master:7077 \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3,org.apache.hadoop:hadoop-aws:3.3.4 \
  /opt/spark-apps/your_script.py
```

### Viewing Data in MinIO

1. Open http://localhost:9001
2. Login with admin/password123
3. Browse the `warehouse` bucket
4. Explore the Iceberg table structure:
   - `/ecommerce/customers/` - Customer data files
   - `/ecommerce/products/` - Product data files
   - `/ecommerce/orders/` - Order data files (partitioned)
   - Metadata files (.metadata.json, manifests, etc.)

### Monitoring Spark Jobs

1. Open http://localhost:8082
2. View running/completed applications
3. Click on an application to see:
   - Executors
   - Job stages
   - Task details

## Troubleshooting

### Spark job fails with S3 connection error
- Ensure MinIO is fully started: `docker-compose ps`
- Check MinIO health: `curl http://localhost:9000/minio/health/live`

### Trino can't read Iceberg tables
- Verify data was written: Check MinIO console for files in `warehouse` bucket
- Check catalog configuration: `docker exec -it trino cat /etc/trino/catalog/iceberg.properties`

### Permission denied errors
- Ensure MinIO credentials match in all configs
- Default: `admin` / `password123`

## Clean Up

```bash
# Stop all services
docker-compose down

# Remove all data (including MinIO volumes)
docker-compose down -v

# Remove generated data
rm -rf spark-data/
```

## Next Steps

1. **Add more data sources**: Ingest CSV, JSON, or streaming data
2. **Implement CDC**: Use Spark Streaming to capture changes
3. **Add data quality checks**: Use Great Expectations or similar
4. **Optimize queries**: Implement materialized views in Trino
5. **Add Hive Metastore**: For centralized catalog management
6. **Implement data governance**: Add Apache Ranger or similar

## Resources

- [Apache Iceberg Documentation](https://iceberg.apache.org/)
- [MinIO Documentation](https://min.io/docs/minio/linux/index.html)
- [Trino Documentation](https://trino.io/docs/current/)
- [Apache Spark Documentation](https://spark.apache.org/docs/latest/)
