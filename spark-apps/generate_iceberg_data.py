#!/usr/bin/env python3
"""
Spark application to generate sample data and write it to MinIO using Iceberg format.
This generates customer orders data with multiple tables using Apache Iceberg.
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, rand, randn, expr, current_timestamp
from pyspark.sql.types import StructType, StructField, StringType, IntegerType, DoubleType, TimestampType
from datetime import datetime, timedelta
import random

def create_spark_session():
    """Create Spark session - configuration is passed via spark-submit."""
    return SparkSession.builder \
        .appName("IcebergDataGenerator") \
        .getOrCreate()

def generate_customers(spark, num_customers=1000):
    """Generate customer data."""
    print(f"Generating {num_customers} customers...")
    
    # Create database if not exists
    try:
        spark.sql("CREATE DATABASE IF NOT EXISTS iceberg.ecommerce")
    except Exception as e:
        print(f"Note: Database might already exist or creating via Spark - continuing: {e}")
    
    # Generate customer data
    customers_data = []
    first_names = ["John", "Jane", "Bob", "Alice", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"]
    last_names = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez"]
    cities = ["New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose"]
    states = ["NY", "CA", "IL", "TX", "AZ", "PA", "TX", "CA", "TX", "CA"]
    
    for i in range(num_customers):
        customer_id = i + 1
        first_name = random.choice(first_names)
        last_name = random.choice(last_names)
        email = f"{first_name.lower()}.{last_name.lower()}{customer_id}@example.com"
        city_idx = random.randint(0, len(cities) - 1)
        city = cities[city_idx]
        state = states[city_idx]
        
        customers_data.append((customer_id, first_name, last_name, email, city, state))
    
    customers_df = spark.createDataFrame(
        customers_data,
        ["customer_id", "first_name", "last_name", "email", "city", "state"]
    )
    
    # Write to Iceberg table
    customers_df.writeTo("iceberg.ecommerce.customers") \
        .using("iceberg") \
        .tableProperty("format-version", "2") \
        .createOrReplace()
    
    print(f"✓ Created Iceberg customers table with {num_customers} records")
    return customers_df

def generate_products(spark, num_products=100):
    """Generate product data."""
    print(f"Generating {num_products} products...")
    
    categories = ["Electronics", "Clothing", "Home & Garden", "Sports", "Books", "Toys", "Food", "Health"]
    product_names = {
        "Electronics": ["Laptop", "Smartphone", "Tablet", "Headphones", "Smart Watch"],
        "Clothing": ["T-Shirt", "Jeans", "Jacket", "Sneakers", "Dress"],
        "Home & Garden": ["Coffee Maker", "Lamp", "Plant Pot", "Pillow", "Rug"],
        "Sports": ["Basketball", "Tennis Racket", "Yoga Mat", "Dumbbells", "Running Shoes"],
        "Books": ["Novel", "Cookbook", "Biography", "Self-Help", "Mystery"],
        "Toys": ["Action Figure", "Board Game", "Puzzle", "Doll", "Building Blocks"],
        "Food": ["Coffee Beans", "Chocolate", "Snack Mix", "Pasta", "Olive Oil"],
        "Health": ["Vitamins", "Protein Powder", "Face Cream", "Shampoo", "Toothpaste"]
    }
    
    products_data = []
    for i in range(num_products):
        product_id = i + 1
        category = random.choice(categories)
        name = random.choice(product_names[category])
        full_name = f"{name} {random.choice(['Pro', 'Premium', 'Classic', 'Deluxe', 'Standard'])}"
        price = round(random.uniform(9.99, 999.99), 2)
        
        products_data.append((product_id, full_name, category, price))
    
    products_df = spark.createDataFrame(
        products_data,
        ["product_id", "name", "category", "price"]
    )
    
    products_df.writeTo("iceberg.ecommerce.products") \
        .using("iceberg") \
        .tableProperty("format-version", "2") \
        .createOrReplace()
    
    print(f"✓ Created Iceberg products table with {num_products} records")
    return products_df

def generate_orders(spark, num_orders=5000):
    """Generate order data with Iceberg partitioning."""
    print(f"Generating {num_orders} orders...")
    
    # Read existing customers and products
    customers_df = spark.table("iceberg.ecommerce.customers")
    products_df = spark.table("iceberg.ecommerce.products")
    
    customer_ids = [row.customer_id for row in customers_df.select("customer_id").collect()]
    product_ids = [row.product_id for row in products_df.select("product_id").collect()]
    product_prices = {row.product_id: row.price for row in products_df.select("product_id", "price").collect()}
    
    orders_data = []
    start_date = datetime(2023, 1, 1)
    end_date = datetime(2024, 11, 25)
    
    statuses = ["pending", "shipped", "delivered", "cancelled"]
    
    for i in range(num_orders):
        order_id = i + 1
        customer_id = random.choice(customer_ids)
        product_id = random.choice(product_ids)
        quantity = random.randint(1, 5)
        price = product_prices[product_id]
        total_amount = round(price * quantity, 2)
        status = random.choice(statuses)
        
        # Random date between start and end
        time_between_dates = end_date - start_date
        days_between_dates = time_between_dates.days
        random_number_of_days = random.randrange(days_between_dates)
        order_date = start_date + timedelta(days=random_number_of_days)
        
        orders_data.append((order_id, customer_id, product_id, quantity, total_amount, order_date, status))
    
    orders_df = spark.createDataFrame(
        orders_data,
        ["order_id", "customer_id", "product_id", "quantity", "total_amount", "order_date", "status"]
    )
    
    # Write to Iceberg table with hidden partitioning by status
    orders_df.writeTo("iceberg.ecommerce.orders") \
        .using("iceberg") \
        .tableProperty("format-version", "2") \
        .partitionedBy("status") \
        .createOrReplace()
    
    print(f"✓ Created Iceberg orders table with {num_orders} records (partitioned by status)")
    return orders_df

def print_sample_data(spark):
    """Print sample data from the generated tables."""
    print("\n" + "="*80)
    print("SAMPLE DATA")
    print("="*80)
    
    print("\n--- Customers (first 10) ---")
    spark.table("iceberg.ecommerce.customers").show(10, truncate=False)
    
    print("\n--- Products (first 10) ---")
    spark.table("iceberg.ecommerce.products").show(10, truncate=False)
    
    print("\n--- Orders (first 10) ---")
    spark.table("iceberg.ecommerce.orders").show(10, truncate=False)
    
    print("\n--- Order Summary by Status ---")
    spark.sql("""
        SELECT status, 
               COUNT(*) as order_count,
               ROUND(SUM(total_amount), 2) as total_revenue
        FROM iceberg.ecommerce.orders
        GROUP BY status
        ORDER BY order_count DESC
    """).show()
    
    print("\n--- Top 10 Customers by Revenue ---")
    spark.sql("""
        SELECT c.customer_id, 
               c.first_name, 
               c.last_name,
               c.city,
               COUNT(o.order_id) as order_count,
               ROUND(SUM(o.total_amount), 2) as total_spent
        FROM iceberg.ecommerce.customers c
        JOIN iceberg.ecommerce.orders o ON c.customer_id = o.customer_id
        GROUP BY c.customer_id, c.first_name, c.last_name, c.city
        ORDER BY total_spent DESC
        LIMIT 10
    """).show(truncate=False)
    
    # Show Iceberg table metadata
    print("\n--- Iceberg Table Metadata ---")
    print("\nOrders table history:")
    spark.sql("SELECT * FROM iceberg.ecommerce.orders.history").show(truncate=False)
    
    print("\nOrders table snapshots:")
    spark.sql("SELECT * FROM iceberg.ecommerce.orders.snapshots").show(truncate=False)

def main():
    """Main function to generate all data."""
    print("Starting Iceberg Data Generation...")
    print("="*80)
    
    spark = create_spark_session()
    
    try:
        # Generate data
        generate_customers(spark, num_customers=1000)
        generate_products(spark, num_products=100)
        generate_orders(spark, num_orders=5000)
        
        # Print samples
        print_sample_data(spark)
        
        print("\n" + "="*80)
        print("✓ Iceberg data generation completed successfully!")
        print("="*80)
        print("\nYou can now query the data using Trino:")
        print("  docker exec -it trino trino")
        print("  trino> SHOW SCHEMAS IN iceberg;")
        print("  trino> USE iceberg.ecommerce;")
        print("  trino> SHOW TABLES;")
        print("  trino> SELECT * FROM customers LIMIT 10;")
        print("\nIceberg-specific features:")
        print("  trino> SELECT * FROM customers FOR VERSION AS OF <snapshot-id>;  -- Time travel")
        print("  trino> SELECT * FROM \"customers$history\";  -- View table history")
        print("  trino> SELECT * FROM \"customers$snapshots\";  -- View snapshots")
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        spark.stop()

if __name__ == "__main__":
    main()
