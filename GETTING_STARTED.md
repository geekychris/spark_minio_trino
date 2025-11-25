# Getting Started with the Lakehouse Demo

## What is this?

This is a **complete modern data lakehouse** running entirely in Docker containers on your local machine. It demonstrates how to:

- Store data in object storage (MinIO - like AWS S3)
- Use Parquet for columnar storage format
- Process data with Apache Spark
- Query data with Trino (a distributed SQL engine)

## The Stack

```
┌──────────────────────────────────────────────────────────────┐
│                        Your Laptop                            │
│                                                               │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │   Trino    │  │   Spark    │  │   MinIO    │            │
│  │ (SQL Query)│  │ (Process)  │  │ (Storage)  │            │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘            │
│        │               │               │                     │
│        │    ┌──────────▼───────────────▼──────┐            │
│        └────►      Parquet Tables              │            │
│             │  (Columnar, Compressed, Fast)    │            │
│             └──────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────┘
```

## 3-Step Quick Start

### Step 1: Start the Services (1 minute)

```bash
docker-compose up -d
```

Wait about 30 seconds for all services to initialize.

Then fix Spark permissions (one-time):
```bash
docker exec -u root spark-master mkdir -p /home/spark/.ivy2/cache
docker exec -u root spark-master chown -R spark:spark /home/spark/.ivy2
```

### Step 2: Generate Data (2-3 minutes)

**Python version:**
```bash
./run_spark_app.sh
```

**Java version:**
```bash
./run_spark_app_java.sh
```

Either version creates an e-commerce dataset with:
- 1,000 customers
- 100 products  
- 5,000 orders

See `JAVA_VERSION.md` for comparison between Python and Java implementations.

### Step 3: Query with SQL (immediate)

```bash
docker exec -it trino trino
```

Then run some queries:

```sql
-- See what's available
SHOW SCHEMAS IN hive;
USE hive.ecommerce;
SHOW TABLES;

-- Simple query
SELECT * FROM customers LIMIT 10;

-- Join query
SELECT 
    c.first_name || ' ' || c.last_name as customer,
    p.name as product,
    o.total_amount
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN products p ON o.product_id = p.product_id
ORDER BY o.total_amount DESC
LIMIT 10;
```

## What Just Happened?

1. **Spark** generated realistic data and wrote it to **Parquet files**
2. The data is stored in **MinIO** (S3-compatible storage) in columnar format
3. **Trino** reads the Parquet files from MinIO and lets you query with SQL
4. Data is partitioned by status for efficient querying

## Explore the Web UIs

Open these in your browser:

### 1. MinIO Console (Storage)
- URL: http://localhost:9001
- Login: `admin` / `password123`
- **What to see**: Browse the `warehouse` bucket to see Parquet data files

### 2. Spark Master (Processing)
- URL: http://localhost:8082
- **What to see**: View completed and running Spark applications

### 3. Trino Web UI (Querying)
- URL: http://localhost:8081
- **What to see**: Monitor SQL queries and cluster status

## Key Lakehouse Features

### 📊 Columnar Storage
Parquet stores data in columns, making analytics queries much faster:
- Only reads the columns you need
- Excellent compression ratios
- Optimized for aggregations and filters

### ⚡ Partitioning
The orders table is partitioned by status for fast queries:

```sql
-- Only scans 'delivered' partition
SELECT * FROM orders WHERE status = 'delivered';
```

### 🔄 S3-Compatible Storage
All data stored in MinIO can easily migrate to:
- AWS S3
- Google Cloud Storage
- Azure Blob Storage

### 🚀 Distributed Processing
Spark processes data in parallel across workers for speed

## Sample Queries to Try

### Top 10 Customers by Revenue
```sql
SELECT 
    c.first_name || ' ' || c.last_name as customer,
    c.city,
    COUNT(o.order_id) as num_orders,
    ROUND(SUM(o.total_amount), 2) as total_spent
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name, c.city
ORDER BY total_spent DESC
LIMIT 10;
```

### Revenue by Product Category
```sql
SELECT 
    p.category,
    COUNT(DISTINCT o.customer_id) as unique_customers,
    COUNT(o.order_id) as total_orders,
    ROUND(SUM(o.total_amount), 2) as revenue
FROM products p
JOIN orders o ON p.product_id = o.product_id
GROUP BY p.category
ORDER BY revenue DESC;
```

### Orders Over Time
```sql
SELECT 
    DATE_TRUNC('month', order_date) as month,
    status,
    COUNT(*) as order_count,
    ROUND(SUM(total_amount), 2) as revenue
FROM orders
GROUP BY DATE_TRUNC('month', order_date), status
ORDER BY month DESC, status;
```

## Next Steps

### Write Your Own Data
1. Create a new PySpark script in `spark-apps/`
2. Run it with `./run_spark_app.sh your_script.py`

### Add More Spark Workers
```bash
docker-compose up -d --scale spark-worker=3
```

### Experiment with Schema Changes
```sql
-- Add a column
ALTER TABLE lakehouse.ecommerce.products 
ADD COLUMN stock_quantity INTEGER;

-- Query still works!
SELECT * FROM products LIMIT 10;
```

### Try Incremental Updates
Instead of `createOrReplace`, use `append` mode in Spark:

```python
df.writeTo("lakehouse.ecommerce.orders").append()
```

## Troubleshooting

**Spark job fails?**
- Wait 30 seconds after starting services for Spark to initialize
- Check logs: `docker-compose logs spark-master`

**Can't see tables in Trino?**
- Make sure you ran `./run_spark_app.sh` first
- Verify data exists: http://localhost:9001 (MinIO console)

**Port already in use?**
- Change ports in `docker-compose.yml`
- Common conflicts: 8082 (Spark), 8081 (Trino), 9000/9001 (MinIO)

## Stop Everything

```bash
# Stop services (keeps data)
docker-compose down

# Stop and delete all data
docker-compose down -v
```

## Learn More

- **Full Documentation**: [LAKEHOUSE_DEMO.md](LAKEHOUSE_DEMO.md)
- **Command Reference**: [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
- **Original Trino Setup**: [README.md](README.md)

## Why is this Cool?

This is a **production-ready architecture** running on your laptop:

✅ Same tools used by companies like Netflix, Apple, LinkedIn  
✅ Petabyte-scale capability (when deployed to cloud)  
✅ Open source - no vendor lock-in  
✅ ACID transactions - enterprise-grade reliability  
✅ Time travel - audit and debug easily  
✅ Schema evolution - adapt without downtime  

You're running a mini version of what powers modern data platforms! 🚀
