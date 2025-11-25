# Java Spark Application

A Java implementation of the data generation app is now available alongside the Python version.

## Quick Comparison

| Feature | Python Version | Java Version |
|---------|---------------|--------------|
| **File** | `spark-apps/generate_data.py` | `spark-apps-java/src/.../DataGenerator.java` |
| **Run Command** | `./run_spark_app.sh` | `./run_spark_app_java.sh` |
| **Build Required** | No | Yes (Maven) |
| **Dependencies** | Bundled with Spark | Maven manages |
| **Lines of Code** | ~230 | ~311 |
| **Type Safety** | Dynamic | Static (compile-time) |

## Files Created

```
spark-apps-java/
├── .gitignore                    # Git ignore rules
├── README.md                     # Detailed Java app documentation
├── pom.xml                       # Maven build configuration
└── src/
    └── main/
        └── java/
            └── com/
                └── lakehouse/
                    └── demo/
                        └── DataGenerator.java   # Main application class
```

## Running the Java Version

### Prerequisites

Make sure the services are running and Spark permissions are set:

```bash
# Start services
docker-compose up -d

# Fix Spark permissions (one-time)
docker exec -u root spark-master mkdir -p /home/spark/.ivy2/cache
docker exec -u root spark-master chown -R spark:spark /home/spark/.ivy2
```

### Execute

```bash
./run_spark_app_java.sh
```

This will:
1. Build the Java application with Maven (in Docker)
2. Create a shaded JAR with all dependencies
3. Submit the JAR to the Spark cluster
4. Generate the data and write to MinIO

## What's Different from Python?

### Code Structure

**Python** uses a procedural approach with functions:
```python
def generate_customers(spark, num_customers=1000):
    # Generate data
    customers_df = spark.createDataFrame(...)
    customers_df.write.saveAsTable("ecommerce.customers")
```

**Java** uses object-oriented approach with static methods:
```java
private static void generateCustomers(SparkSession spark, int numCustomers) {
    // Define schema
    StructType schema = new StructType(new StructField[]{...});
    // Generate data
    Dataset<Row> customersDF = spark.createDataFrame(rows, schema);
    customersDF.write().saveAsTable("ecommerce.customers");
}
```

### Schema Definition

**Python** infers schemas from data:
```python
customers_df = spark.createDataFrame(
    customers_data,
    ["customer_id", "first_name", "last_name", "email", "city", "state"]
)
```

**Java** requires explicit schema definition:
```java
StructType schema = new StructType(new StructField[]{
    DataTypes.createStructField("customer_id", DataTypes.IntegerType, false),
    DataTypes.createStructField("first_name", DataTypes.StringType, false),
    // ...
});
```

### Build Process

**Python**: No build needed, runs directly
```bash
spark-submit generate_data.py
```

**Java**: Maven build creates JAR
```bash
mvn clean package
spark-submit --class com.lakehouse.demo.DataGenerator target/spark-data-generator-1.0-SNAPSHOT.jar
```

## When to Use Which?

### Use Python when:
- ✅ Rapid prototyping and iteration
- ✅ Data exploration and analysis
- ✅ Working with data scientists
- ✅ Simple transformations
- ✅ Ad-hoc scripts

### Use Java when:
- ✅ Production-grade applications
- ✅ Complex business logic
- ✅ Integration with Java/JVM ecosystem
- ✅ Team is primarily Java developers
- ✅ Need strong compile-time type checking
- ✅ Long-term maintainability is priority

## Performance

Both versions generate identical data and have similar performance characteristics:
- Same Spark runtime (JVM)
- Same data processing algorithms
- Same Parquet output format
- Python has slight overhead for interpreter, but negligible for I/O-bound operations
- Java may have advantage for CPU-intensive transformations

## Future Enhancements

Possible improvements for the Java version:

1. **Separation of Concerns**: Split into separate classes
   ```
   - CustomerGenerator.java
   - ProductGenerator.java
   - OrderGenerator.java
   ```

2. **Configuration**: Externalize configuration
   ```java
   - DatabaseConfig.java
   - S3Config.java
   ```

3. **Testing**: Add unit tests
   ```
   - DataGeneratorTest.java
   - SchemaValidationTest.java
   ```

4. **Logging**: Use SLF4J/Log4j instead of System.out

5. **CLI Arguments**: Support command-line parameters
   ```bash
   --num-customers 2000 --num-products 200
   ```

## Docker Compose Changes

The `docker-compose.yml` was updated to mount the Java project:

```yaml
volumes:
  - ./spark-apps:/opt/spark-apps
  - ./spark-apps-java:/opt/spark-apps-java  # Added
  - ./spark-data:/opt/spark-data
```

This allows the Spark containers to access the compiled JAR file.

## Summary

You now have two equivalent implementations:
- **Python**: Quick, flexible, great for data science
- **Java**: Robust, typed, great for production

Both produce the same output and can be used interchangeably based on your needs and team preferences!
