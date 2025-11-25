# MinIO + Spark + Trino Lakehouse Demo

This demo shows how to use MinIO for S3-compatible storage with Spark for data generation and Trino for SQL queries, all using Parquet format.

## Architecture

- **MinIO**: S3-compatible object storage
- **Spark**: Data generation and processing (with embedded Derby metastore)
- **Trino**: SQL query engine with Hive connector

## Quick Start

### 1. Start all services

```bash
docker-compose up -d
```

Wait for all services to become healthy (~30 seconds).

### 2. Fix Spark permissions (one-time setup)

```bash
docker exec -u root spark-master mkdir -p /home/spark/.ivy2/cache
docker exec -u root spark-master chown -R spark:spark /home/spark/.ivy2
```

### 3. Generate sample data

**Python version:**
```bash
./run_spark_app.sh
```

**Java version:**
```bash
./run_spark_app_java.sh
```

Either version will generate 3 tables in the `ecommerce` database:
- `customers` (1,000 records)
- `products` (100 records)  
- `orders` (5,000 records, partitioned by status)

See `spark-apps-java/README.md` for details on the Java implementation.

### 4. Create tables in Trino

```bash
# Create schema
docker exec trino trino --execute "CREATE SCHEMA IF NOT EXISTS hive.ecommerce WITH (location='s3a://warehouse/ecommerce.db/');"

# Create customers table
docker exec trino trino --execute "CREATE TABLE IF NOT EXISTS hive.ecommerce.customers (customer_id INT, first_name VARCHAR, last_name VARCHAR, email VARCHAR, city VARCHAR, state VARCHAR) WITH (external_location='s3a://warehouse/ecommerce.db/customers/', format='PARQUET');"

# Create products table
docker exec trino trino --execute "CREATE TABLE IF NOT EXISTS hive.ecommerce.products (product_id INT, name VARCHAR, category VARCHAR, price DOUBLE) WITH (external_location='s3a://warehouse/ecommerce.db/products/', format='PARQUET');"

# Create orders table (partitioned by status)
docker exec trino trino --execute "CREATE TABLE IF NOT EXISTS hive.ecommerce.orders (order_id INT, customer_id INT, product_id INT, quantity INT, total_amount DOUBLE, order_date TIMESTAMP, status VARCHAR) WITH (external_location='s3a://warehouse/ecommerce.db/orders/', format='PARQUET', partitioned_by=ARRAY['status']);"
```

### 5. Note about partitioned tables

**Current limitation**: The file-based Hive metastore has issues with external partitioned tables. The `customers` and `products` tables work perfectly. The `orders` table is created but partition discovery doesn't work automatically with the current configuration.

**Workaround**: You can still query all the data by reading the Parquet files directly or by configuring a proper Hive metastore server (not included in this simplified demo).

### 6. Query data with Trino

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

## Service URLs

- **MinIO Console**: http://localhost:9001 (admin / password123)
- **Spark Master UI**: http://localhost:8082
- **Trino UI**: http://localhost:8081

## Checking Data in MinIO

```bash
# Set up MinIO client alias
docker exec minio mc alias set myminio http://localhost:9000 admin password123

# List data in warehouse
docker exec minio mc ls myminio/warehouse/ --recursive
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

## Stopping Services

```bash
docker-compose down

# To also remove volumes and data:
docker-compose down -v
```

## Troubleshooting

### Spark ivy cache permission errors
Run the fix from step 2 in Quick Start.

### Trino shows empty results for orders table
Make sure you've registered the partitions (step 5).

### Trino server is still initializing
Wait 30-60 seconds after starting services for Trino to fully initialize.

### Connection refused to MinIO
Ensure MinIO container is healthy: `docker ps` should show "healthy" status.
