# Java Spark Iceberg Data Generator

This is a Java implementation of the Spark Iceberg data generation application. It generates e-commerce data (customers, products, orders) and writes them as Apache Iceberg tables to MinIO with HMS integration.

## Prerequisites

- Docker (for running Maven and Spark)
- The lakehouse demo services running (`docker-compose up -d`)
- Hive Metastore Service (HMS) running

## Project Structure

```
spark-apps-java-iceberg/
├── pom.xml                                      # Maven build configuration
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── lakehouse/
│                   └── iceberg/
│                       └── IcebergDataGenerator.java   # Main application
└── target/                                      # Build output (after mvn package)
    └── spark-iceberg-generator-1.0-SNAPSHOT.jar
```

## Building and Running

### Quick Start

From the main demo directory:

```bash
./run_iceberg_spark_app_java.sh
```

This script will:
1. Build the Java application using Maven (in a Docker container)
2. Submit the JAR to the Spark cluster with Iceberg configuration
3. Generate the data and write to MinIO as Iceberg tables

### Manual Build

If you want to build manually:

```bash
cd spark-apps-java-iceberg

# Build with Maven (using Docker)
docker run --rm \
  -v "$(pwd)":/app \
  -v "$HOME/.m2":/root/.m2 \
  -w /app \
  maven:3.8.6-eclipse-temurin-11 \
  mvn clean package

# Or if you have Maven installed locally:
mvn clean package
```

### Manual Run

After building, submit to Spark:

```bash
docker exec spark-master /opt/spark/bin/spark-submit \
  --master spark://spark-master:7077 \
  --deploy-mode client \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3,org.apache.hadoop:hadoop-aws:3.3.4,com.amazonaws:aws-java-sdk-bundle:1.12.262 \
  --conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions \
  --conf spark.sql.catalog.iceberg=org.apache.iceberg.spark.SparkCatalog \
  --conf spark.sql.catalog.iceberg.type=hive \
  --conf spark.sql.catalog.iceberg.uri=thrift://hive-metastore:9083 \
  --conf spark.sql.catalog.iceberg.warehouse=s3a://iceberg/warehouse \
  --conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
  --conf spark.hadoop.fs.s3a.access.key=admin \
  --conf spark.hadoop.fs.s3a.secret.key=password123 \
  --conf spark.hadoop.fs.s3a.path.style.access=true \
  --conf spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem \
  --conf spark.hadoop.fs.s3a.connection.ssl.enabled=false \
  --class com.lakehouse.iceberg.IcebergDataGenerator \
  /opt/spark-apps-java-iceberg/target/spark-iceberg-generator-1.0-SNAPSHOT.jar
```

## What It Does

The application generates Iceberg tables:

1. **Customers Table** (1,000 records)
   - customer_id, first_name, last_name, email, city, state
   - Stored as Iceberg in: s3a://iceberg/warehouse/ecommerce.db/customers/

2. **Products Table** (100 records)
   - product_id, name, category, price
   - 8 categories: Electronics, Clothing, Home & Garden, Sports, Books, Toys, Food, Health
   - Stored as Iceberg in: s3a://iceberg/warehouse/ecommerce.db/products/

3. **Orders Table** (5,000 records)
   - order_id, customer_id, product_id, quantity, total_amount, order_date, status
   - Partitioned by status: cancelled, delivered, pending, shipped
   - Stored as Iceberg in: s3a://iceberg/warehouse/ecommerce.db/orders/

## Iceberg Features

Unlike the Parquet version, this uses **Apache Iceberg** which provides:

- **ACID Transactions**: Full consistency guarantees
- **Time Travel**: Query historical data using snapshots
- **Schema Evolution**: Add/remove/rename columns safely
- **Hidden Partitioning**: Users don't need to know partition schemes
- **Fast Metadata Operations**: Efficient filtering via manifest files
- **Concurrent Writes**: Multiple writers can safely write

## Configuration

The application uses:
- **HMS**: Hive Metastore Service at `thrift://hive-metastore:9083`
- **MinIO**: S3-compatible storage at `http://minio:9000`
- **Credentials**: admin / password123
- **Warehouse**: `s3a://iceberg/warehouse`
- **Format**: Iceberg v2 with Parquet data files

All Spark configuration is passed via `spark-submit` command line.

## Dependencies

Defined in `pom.xml`:
- Apache Spark 3.5.0 (Core, SQL)
- Apache Iceberg 1.4.3 (Spark Runtime)
- Hadoop AWS 3.3.4 (for S3A filesystem)
- AWS SDK Bundle 1.12.262

All dependencies are marked as `provided` scope since they're available in the Spark runtime.

## Querying Iceberg Tables

After generating data, query with Trino:

```sql
-- Basic queries
SHOW SCHEMAS IN iceberg;
USE iceberg.ecommerce;
SHOW TABLES;
SELECT * FROM customers LIMIT 10;

-- Time travel
SELECT * FROM "orders$history";
SELECT * FROM "orders$snapshots";
SELECT * FROM orders FOR VERSION AS OF <snapshot-id>;

-- Partitioning
SELECT * FROM "orders$partitions";

-- Maintenance
ALTER TABLE orders EXECUTE expire_snapshots(retention_threshold => '7d');
```

## Comparison with Parquet Version

### Iceberg Advantages
- ACID transactions with full consistency
- Time travel and snapshot isolation
- Hidden partitioning (users don't specify partition filters)
- Schema evolution without rewriting data
- Better metadata pruning and query performance
- Concurrent writers without conflicts

### Parquet Advantages
- Simpler setup (no HMS required)
- Smaller metadata overhead
- Direct file access without catalog

## Comparison with Python Version

Both Python and Java Iceberg implementations:
- Generate the same data structure and volume
- Use the same Iceberg APIs
- Write to the same MinIO location
- Support all Iceberg features

**Java Version Benefits:**
- Strongly typed with compile-time checks
- Better IDE support and refactoring
- Potentially better performance
- Familiar for Java/JVM developers

**Python Version Benefits:**
- More concise code
- Easier to prototype
- Better for data science workflows

## Troubleshooting

### Build fails with "Cannot resolve dependencies"
Ensure internet connectivity. First build takes longer as Maven downloads dependencies.

### ClassNotFoundException for Iceberg classes
The `--packages` flag in spark-submit will download Iceberg JARs automatically.

### Connection refused to Hive Metastore
Ensure HMS is running and healthy:
```bash
docker ps | grep hive-metastore
docker logs hive-metastore
```

### Database creation fails
The HMS needs S3 configuration which is provided via mounted `hive-conf/core-site.xml`.

### Ivy cache permissions error
Fix Spark permissions:
```bash
docker exec -u root spark-master mkdir -p /home/spark/.ivy2/cache
docker exec -u root spark-master chown -R spark:spark /home/spark/.ivy2
```

## Advanced Usage

### Incremental Updates

```java
// Append instead of overwrite
ordersDF.writeTo("iceberg.ecommerce.orders")
    .using("iceberg")
    .append();
```

### Schema Evolution

```sql
-- Add column
ALTER TABLE iceberg.ecommerce.customers ADD COLUMN phone VARCHAR;

-- Rename column  
ALTER TABLE iceberg.ecommerce.customers RENAME COLUMN email TO email_address;
```

### Table Maintenance

Run compaction and cleanup:

```sql
-- Rewrite small files
ALTER TABLE orders EXECUTE rewrite_data_files;

-- Remove orphan files
ALTER TABLE orders EXECUTE remove_orphan_files(older_than => TIMESTAMP '2024-11-20 00:00:00');

-- Expire old snapshots
ALTER TABLE orders EXECUTE expire_snapshots(retention_threshold => '7d');
```

## Next Steps

- Modify data generation logic in `IcebergDataGenerator.java`
- Implement incremental updates instead of full refresh
- Add data quality checks
- Create separate classes for each entity
- Add support for MERGE operations
- Implement CDC (Change Data Capture) patterns

## License

This is demo code for the lakehouse project.
