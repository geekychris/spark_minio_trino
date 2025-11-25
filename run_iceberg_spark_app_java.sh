#!/bin/bash
# Run the Java Iceberg data generation Spark application

echo "Building Java Iceberg application..."
echo ""

# Build with Maven using Docker
docker run --rm \
  -v "$(pwd)/spark-apps-java-iceberg":/app \
  -v "$HOME/.m2":/root/.m2 \
  -w /app \
  maven:3.8.6-eclipse-temurin-11 \
  mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo ""
echo "Copying JAR to Spark container..."
# Copy the built JAR to the mounted spark-apps-java directory so it's accessible in the container
mkdir -p spark-apps-java-iceberg/target
cp spark-apps-java-iceberg/target/spark-iceberg-generator-1.0-SNAPSHOT.jar spark-apps-java/spark-iceberg-generator-1.0-SNAPSHOT.jar

echo ""
echo "Running Java Iceberg Spark application..."
echo "This will generate sample data in Iceberg format and store it in MinIO."
echo ""

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
  /opt/spark-apps-java/spark-iceberg-generator-1.0-SNAPSHOT.jar

echo ""
echo "Done! Check the output above for any errors."
