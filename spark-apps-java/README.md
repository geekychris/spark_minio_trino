# Java Spark Data Generator

This is a Java implementation of the Spark data generation application. It generates the same e-commerce dataset (customers, products, orders) as the Python version, but uses Java and Maven for building.

## Prerequisites

- Docker (for running Maven and Spark)
- The lakehouse demo services running (`docker-compose up -d`)

## Project Structure

```
spark-apps-java/
├── pom.xml                           # Maven build configuration
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── lakehouse/
│                   └── demo/
│                       └── DataGenerator.java   # Main application
└── target/                           # Build output (after mvn package)
    └── spark-data-generator-1.0-SNAPSHOT.jar
```

## Building and Running

### Quick Start

From the main demo directory:

```bash
./run_spark_app_java.sh
```

This script will:
1. Build the Java application using Maven (in a Docker container)
2. Submit the JAR to the Spark cluster
3. Generate the data and write to MinIO

### Manual Build

If you want to build manually:

```bash
cd spark-apps-java

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
  --packages org.apache.hadoop:hadoop-aws:3.3.4,com.amazonaws:aws-java-sdk-bundle:1.12.262 \
  --class com.lakehouse.demo.DataGenerator \
  /opt/spark-apps-java/target/spark-data-generator-1.0-SNAPSHOT.jar
```

## What It Does

The application generates:

1. **Customers Table** (1,000 records)
   - customer_id, first_name, last_name, email, city, state
   - Stored as Parquet in: s3a://warehouse/ecommerce.db/customers/

2. **Products Table** (100 records)
   - product_id, name, category, price
   - 8 categories: Electronics, Clothing, Home & Garden, Sports, Books, Toys, Food, Health
   - Stored as Parquet in: s3a://warehouse/ecommerce.db/products/

3. **Orders Table** (5,000 records)
   - order_id, customer_id, product_id, quantity, total_amount, status, order_date
   - Partitioned by status: cancelled, delivered, pending, shipped
   - Stored as Parquet in: s3a://warehouse/ecommerce.db/orders/status=*/

## Configuration

The application is configured to:
- Connect to MinIO at `http://minio:9000`
- Use credentials: admin / password123
- Write to S3A path: `s3a://warehouse/`
- Use Hive metastore with embedded Derby
- Write data in Parquet format with Snappy compression

All configuration is in the `createSparkSession()` method in `DataGenerator.java`.

## Dependencies

Defined in `pom.xml`:
- Apache Spark 3.5.0 (Core, SQL, Hive)
- Hadoop AWS 3.3.4 (for S3A filesystem)
- AWS SDK Bundle 1.12.262

All dependencies are marked as `provided` scope since they're available in the Spark runtime.

## Comparison with Python Version

Both implementations:
- Generate the same data structure and volume
- Use the same Spark APIs (just different language bindings)
- Write to the same MinIO location in Parquet format
- Support partitioning on the orders table

**Advantages of Java version:**
- Strongly typed with compile-time checks
- Better IDE support and refactoring
- Potentially better performance for complex logic
- Familiar for Java/JVM developers

**Advantages of Python version:**
- More concise code
- Easier to prototype
- Better for data science workflows
- Familiar for data scientists

## Troubleshooting

### Build fails with "Cannot resolve dependencies"

Make sure you have internet connectivity for Maven to download dependencies. The first build will take longer as Maven downloads all artifacts.

### Spark submit fails with ClassNotFoundException

Make sure the JAR was built successfully in `target/spark-data-generator-1.0-SNAPSHOT.jar`.

### Connection refused to MinIO

Ensure all services are running:
```bash
docker-compose up -d
docker ps
```

### Ivy cache permissions error

Fix Spark permissions:
```bash
docker exec -u root spark-master mkdir -p /home/spark/.ivy2/cache
docker exec -u root spark-master chown -R spark:spark /home/spark/.ivy2
```

## Next Steps

- Modify the data generation logic in `DataGenerator.java`
- Add more tables or change the schema
- Implement incremental updates instead of overwrite
- Add data quality checks or validation
- Create separate classes for each table's generation logic

## License

This is demo code for the lakehouse project.
