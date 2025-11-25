#!/bin/bash

echo "Building and running Java Spark data generation application..."
echo "This will generate sample e-commerce data in Parquet format and store it in MinIO"
echo ""

# Navigate to Java project directory
cd spark-apps-java

# Build the project with Maven
echo "Building Java application with Maven..."
docker run --rm -v "$(pwd)":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.8.6-eclipse-temurin-11 mvn clean package

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo ""
echo "✓ Build successful!"
echo ""
echo "Running Spark application..."

# Run the application using spark-submit
docker exec spark-master /opt/spark/bin/spark-submit \
  --master spark://spark-master:7077 \
  --deploy-mode client \
  --packages org.apache.hadoop:hadoop-aws:3.3.4,com.amazonaws:aws-java-sdk-bundle:1.12.262 \
  --class com.lakehouse.demo.DataGenerator \
  /opt/spark-apps-java/target/spark-data-generator-1.0-SNAPSHOT.jar

echo ""
echo "✓ Done!"
