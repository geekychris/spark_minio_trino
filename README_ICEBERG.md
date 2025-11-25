# MinIO + Spark + Trino + Iceberg Lakehouse Demo

This demo shows a modern lakehouse architecture using Apache Iceberg with MinIO for object storage, Spark for data generation, and Trino for SQL queries.

## Architecture

- **MinIO**: S3-compatible object storage
- **PostgreSQL**: Metadata database for Hive Metastore
- **Hive Metastore**: Centralized metadata service for Iceberg tables
- **Spark**: Data generation and processing with Iceberg support
- **Trino**: SQL query engine with Iceberg connector

## What is Apache Iceberg?

Apache Iceberg is a high-performance table format for huge analytic datasets. It provides:

- **ACID Transactions**: Full consistency guarantees
- **Time Travel**: Query historical data using snapshots
- **Schema Evolution**: Add, remove, or rename columns safely
- **Hidden Partitioning**: No need to expose partitioning to users
- **Fast Metadata Operations**: Efficient filtering and planning
- **Concurrent Writes**: Multiple writers without conflicts

## Quick Start

### 1. Start all services

```bash
docker-compose up -d
```

Wait for all services to become healthy (~60 seconds). The Hive Metastore needs time to initialize the PostgreSQL schema.

### 2. Check service status

```bash
docker ps
```

All containers should show "healthy" status. If `hive-metastore` is still starting up, wait a bit longer.

### 3. Fix Spark permissions (one-time setup)

```bash
docker exec -u root spark-master mkdir -p /home/spark/.ivy2/cache
docker exec -u root spark-master chown -R spark:spark /home/spark/.ivy2
```

### 4. Generate sample Iceberg data

```bash
./run_iceberg_spark_app.sh
```

This will generate 3 Iceberg tables in the `iceberg.ecommerce` database:
- `customers` (1,000 records)
- `products` (100 records)  
- `orders` (5,000 records, partitioned by status)

The Spark job will:
- Connect to the Hive Metastore Service
- Write data using Iceberg format v2
- Store files in MinIO (s3a://iceberg/warehouse/)
- Register tables in the Hive Metastore

### 5. Query data with Trino

```bash
# Interactive shell
docker exec -it trino trino

# Once in Trino shell:
SHOW CATALOGS;
SHOW SCHEMAS IN iceberg;
USE iceberg.ecommerce;
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

## Iceberg-Specific Features

### Time Travel

Query data as it existed at a specific snapshot:

```sql
-- List available snapshots
SELECT * FROM "orders$snapshots";

-- Query historical data
SELECT * FROM orders FOR VERSION AS OF 1234567890123456789;

-- Query at a specific timestamp
SELECT * FROM orders FOR TIMESTAMP AS OF TIMESTAMP '2024-11-25 10:00:00';
```

### Table History

View the history of changes to a table:

```sql
SELECT * FROM "orders$history";
SELECT * FROM "orders$metadata_log_entries";
```

### Schema Evolution

Safely evolve your table schema:

```sql
-- Add a new column
ALTER TABLE orders ADD COLUMN discount_applied BOOLEAN;

-- Rename a column
ALTER TABLE orders RENAME COLUMN total_amount TO order_total;

-- Drop a column
ALTER TABLE orders DROP COLUMN discount_applied;
```

### Partitioning Information

View partition details:

```sql
SELECT * FROM "orders$partitions";
```

### Table Maintenance

```sql
-- Expire old snapshots (keep last 7 days)
ALTER TABLE orders EXECUTE expire_snapshots(retention_threshold => '7d');

-- Remove orphan files
ALTER TABLE orders EXECUTE remove_orphan_files(older_than => TIMESTAMP '2024-11-20 00:00:00');

-- Rewrite small files
ALTER TABLE orders EXECUTE rewrite_data_files;
```

## Service URLs

- **MinIO Console**: http://localhost:9001 (admin / password123)
- **Spark Master UI**: http://localhost:8082
- **Trino UI**: http://localhost:8081
- **PostgreSQL**: localhost:5432 (hive / hive / metastore)
- **Hive Metastore**: thrift://localhost:9083

## Checking Data in MinIO

```bash
# Set up MinIO client alias
docker exec minio mc alias set myminio http://localhost:9000 admin password123

# List Iceberg data
docker exec minio mc ls myminio/iceberg/warehouse/ --recursive

# List by schema
docker exec minio mc ls myminio/iceberg/warehouse/ecommerce.db/ --recursive
```

## Architecture Benefits

### Why Iceberg over Parquet/Hive?

1. **ACID Guarantees**: Iceberg provides full ACID transactions, unlike raw Parquet files
2. **Better Performance**: Metadata pruning is much faster with Iceberg's manifest files
3. **Hidden Partitioning**: Users don't need to know about partitioning schemes
4. **Schema Evolution**: Add/remove/rename columns without rewriting data
5. **Time Travel**: Query historical versions of data
6. **Concurrent Writes**: Multiple writers can safely write to the same table

### Why Hive Metastore Service?

The file-based metastore used in the basic setup has limitations:
- No support for concurrent access
- Poor performance with many tables
- Limited Iceberg feature support

The Hive Metastore Service (HMS) provides:
- Centralized metadata management
- Concurrent access from multiple clients
- Full Iceberg feature support
- Better performance and reliability

## Data Flow

1. **Spark** generates data and writes Iceberg tables to MinIO
2. **Iceberg format** creates data files + metadata files (manifests, snapshots)
3. **Hive Metastore** stores table definitions and points to Iceberg metadata
4. **Trino** reads table metadata from HMS and queries data files in MinIO

## Example Analytics Queries

```sql
-- Customer distribution by state
SELECT state, COUNT(*) as customer_count 
FROM customers 
GROUP BY state 
ORDER BY customer_count DESC;

-- Product analysis by category
SELECT category, 
       COUNT(*) as product_count, 
       AVG(price) as avg_price,
       MIN(price) as min_price,
       MAX(price) as max_price
FROM products
GROUP BY category
ORDER BY avg_price DESC;

-- Order metrics by status
SELECT status, 
       COUNT(*) as order_count,
       SUM(total_amount) as total_revenue,
       AVG(total_amount) as avg_order_value,
       MIN(order_date) as first_order,
       MAX(order_date) as last_order
FROM orders
GROUP BY status;

-- Top customers by revenue
SELECT c.first_name, c.last_name, c.city, c.state,
       COUNT(o.order_id) as order_count,
       SUM(o.total_amount) as total_spent,
       AVG(o.total_amount) as avg_order_value
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name, c.city, c.state
ORDER BY total_spent DESC
LIMIT 10;

-- Product popularity by category
SELECT p.category, p.name,
       COUNT(o.order_id) as times_ordered,
       SUM(o.quantity) as total_quantity,
       SUM(o.total_amount) as total_revenue
FROM products p
JOIN orders o ON p.product_id = o.product_id
GROUP BY p.product_id, p.category, p.name
ORDER BY total_revenue DESC
LIMIT 20;

-- Time series: Orders by month
SELECT DATE_TRUNC('month', order_date) as order_month,
       COUNT(*) as order_count,
       SUM(total_amount) as monthly_revenue
FROM orders
GROUP BY DATE_TRUNC('month', order_date)
ORDER BY order_month;
```

## Updating Existing Data

With Iceberg, you can update and delete data:

```sql
-- Update prices
UPDATE products SET price = price * 1.1 WHERE category = 'Electronics';

-- Cancel old pending orders
UPDATE orders SET status = 'cancelled' 
WHERE status = 'pending' AND order_date < DATE '2024-01-01';

-- Delete test data
DELETE FROM customers WHERE email LIKE '%test%';
```

These operations create new snapshots, allowing you to time travel back to previous states.

## Stopping Services

```bash
docker-compose down

# To also remove volumes and data:
docker-compose down -v
```

## Troubleshooting

### Spark ivy cache permission errors
Run the fix from step 3 in Quick Start.

### Hive Metastore not starting
Check logs: `docker logs hive-metastore`
Ensure PostgreSQL is healthy: `docker ps | grep postgres`

### Trino shows "Schema not found"
Wait for the Spark job to complete and create the tables first.

### Connection refused to MinIO or HMS
Ensure containers are healthy: `docker ps`
Wait 60 seconds after `docker-compose up` for all services to initialize.

### Iceberg table not found in Trino
Verify the table exists in HMS:
```bash
docker exec hive-metastore /opt/hive/bin/beeline -u "jdbc:hive2://" -e "SHOW TABLES IN ecommerce;"
```

## Comparing with Old Setup

The previous setup used:
- File-based Hive metastore (limited features)
- Raw Parquet files (no ACID, no time travel)
- Manual partition management

The new Iceberg setup provides:
- Full HMS with PostgreSQL backend
- Iceberg tables with ACID and time travel
- Automatic partition management
- Better performance and reliability

Both `hive` and `iceberg` catalogs are available in Trino, so you can use both approaches side-by-side.
