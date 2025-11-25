#!/bin/bash
# Script to run the Spark data generation application

echo "Running Spark data generation application..."
echo "This will generate sample e-commerce data in Iceberg format and store it in MinIO"
echo ""

docker exec -it spark-master /opt/spark/bin/spark-submit \
  --master spark://spark-master:7077 \
  --deploy-mode client \
--packages org.apache.hadoop:hadoop-aws:3.3.4,com.amazonaws:aws-java-sdk-bundle:1.12.262 \
  /opt/spark-apps/generate_data.py
