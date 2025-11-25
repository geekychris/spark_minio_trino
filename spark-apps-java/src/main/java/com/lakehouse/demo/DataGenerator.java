package com.lakehouse.demo;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spark application to generate sample e-commerce data and write it to Parquet format in MinIO.
 * Generates customers, products, and orders tables with realistic data.
 */
public class DataGenerator {
    
    private static final Random random = new Random();
    
    // Sample data arrays
    private static final String[] FIRST_NAMES = {
        "John", "Jane", "Bob", "Alice", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"
    };
    
    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez"
    };
    
    private static final String[] CITIES = {
        "New York", "Los Angeles", "Chicago", "Houston", "Phoenix", 
        "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose"
    };
    
    private static final String[] STATES = {
        "NY", "CA", "IL", "TX", "AZ", "PA", "TX", "CA", "TX", "CA"
    };
    
    private static final String[] CATEGORIES = {
        "Electronics", "Clothing", "Home & Garden", "Sports", "Books", "Toys", "Food", "Health"
    };
    
    private static final String[][] PRODUCT_NAMES = {
        {"Laptop", "Smartphone", "Tablet", "Headphones", "Smart Watch"},
        {"T-Shirt", "Jeans", "Jacket", "Sneakers", "Dress"},
        {"Coffee Maker", "Lamp", "Plant Pot", "Pillow", "Rug"},
        {"Basketball", "Tennis Racket", "Yoga Mat", "Dumbbells", "Running Shoes"},
        {"Novel", "Cookbook", "Biography", "Self-Help", "Mystery"},
        {"Action Figure", "Board Game", "Puzzle", "Doll", "Building Blocks"},
        {"Coffee Beans", "Chocolate", "Snack Mix", "Pasta", "Olive Oil"},
        {"Vitamins", "Protein Powder", "Face Cream", "Shampoo", "Toothpaste"}
    };
    
    private static final String[] PRODUCT_MODIFIERS = {
        "Pro", "Premium", "Classic", "Deluxe", "Standard"
    };
    
    private static final String[] ORDER_STATUSES = {
        "pending", "shipped", "delivered", "cancelled"
    };

    public static void main(String[] args) {
        System.out.println("Starting Data Generation...");
        System.out.println("=".repeat(80));
        
        SparkSession spark = createSparkSession();
        
        try {
            // Generate data
            int numCustomers = 1000;
            int numProducts = 100;
            int numOrders = 5000;
            
            generateCustomers(spark, numCustomers);
            generateProducts(spark, numProducts);
            generateOrders(spark, numOrders);
            
            // Print sample data
            printSampleData(spark);
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("✓ Data generation completed successfully!");
            System.out.println("=".repeat(80));
            System.out.println("\nYou can now query the data using Trino:");
            System.out.println("  docker exec -it trino trino");
            System.out.println("  trino> SHOW SCHEMAS IN hive;");
            System.out.println("  trino> USE hive.ecommerce;");
            System.out.println("  trino> SHOW TABLES;");
            System.out.println("  trino> SELECT * FROM customers LIMIT 10;");
            
        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            spark.stop();
        }
    }
    
    private static SparkSession createSparkSession() {
        return SparkSession.builder()
            .appName("ParquetDataGenerator-Java")
            .config("spark.jars.packages", 
                "org.apache.hadoop:hadoop-aws:3.3.4," +
                "com.amazonaws:aws-java-sdk-bundle:1.12.262")
            .config("spark.sql.warehouse.dir", "s3a://warehouse/")
            .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000")
            .config("spark.hadoop.fs.s3a.access.key", "admin")
            .config("spark.hadoop.fs.s3a.secret.key", "password123")
            .config("spark.hadoop.fs.s3a.path.style.access", "true")
            .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
            .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
            .config("spark.sql.catalogImplementation", "hive")
            .config("javax.jdo.option.ConnectionURL", 
                "jdbc:derby:;databaseName=/opt/spark-data/metastore_db;create=true")
            .enableHiveSupport()
            .getOrCreate();
    }
    
    private static void generateCustomers(SparkSession spark, int numCustomers) {
        System.out.println("Generating " + numCustomers + " customers...");
        
        // Create database
        spark.sql("CREATE DATABASE IF NOT EXISTS ecommerce");
        
        // Define schema
        StructType schema = new StructType(new StructField[]{
            DataTypes.createStructField("customer_id", DataTypes.IntegerType, false),
            DataTypes.createStructField("first_name", DataTypes.StringType, false),
            DataTypes.createStructField("last_name", DataTypes.StringType, false),
            DataTypes.createStructField("email", DataTypes.StringType, false),
            DataTypes.createStructField("city", DataTypes.StringType, false),
            DataTypes.createStructField("state", DataTypes.StringType, false)
        });
        
        // Generate data
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < numCustomers; i++) {
            int customerId = i + 1;
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String email = String.format("%s.%s%d@example.com", 
                firstName.toLowerCase(), lastName.toLowerCase(), customerId);
            int cityIdx = random.nextInt(CITIES.length);
            String city = CITIES[cityIdx];
            String state = STATES[cityIdx];
            
            rows.add(RowFactory.create(customerId, firstName, lastName, email, city, state));
        }
        
        Dataset<Row> customersDF = spark.createDataFrame(rows, schema);
        
        // Write to Hive table as Parquet
        customersDF.write()
            .mode(SaveMode.Overwrite)
            .format("parquet")
            .saveAsTable("ecommerce.customers");
        
        System.out.println("✓ Created customers table with " + numCustomers + " records");
    }
    
    private static void generateProducts(SparkSession spark, int numProducts) {
        System.out.println("Generating " + numProducts + " products...");
        
        // Define schema
        StructType schema = new StructType(new StructField[]{
            DataTypes.createStructField("product_id", DataTypes.IntegerType, false),
            DataTypes.createStructField("name", DataTypes.StringType, false),
            DataTypes.createStructField("category", DataTypes.StringType, false),
            DataTypes.createStructField("price", DataTypes.DoubleType, false)
        });
        
        // Generate data
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < numProducts; i++) {
            int productId = i + 1;
            int categoryIdx = random.nextInt(CATEGORIES.length);
            String category = CATEGORIES[categoryIdx];
            String[] names = PRODUCT_NAMES[categoryIdx];
            String name = names[random.nextInt(names.length)];
            String modifier = PRODUCT_MODIFIERS[random.nextInt(PRODUCT_MODIFIERS.length)];
            String fullName = name + " " + modifier;
            double price = Math.round((9.99 + random.nextDouble() * 990.0) * 100.0) / 100.0;
            
            rows.add(RowFactory.create(productId, fullName, category, price));
        }
        
        Dataset<Row> productsDF = spark.createDataFrame(rows, schema);
        
        // Write to Hive table as Parquet
        productsDF.write()
            .mode(SaveMode.Overwrite)
            .format("parquet")
            .saveAsTable("ecommerce.products");
        
        System.out.println("✓ Created products table with " + numProducts + " records");
    }
    
    private static void generateOrders(SparkSession spark, int numOrders) {
        System.out.println("Generating " + numOrders + " orders...");
        
        // Read existing customers and products
        Dataset<Row> customersDF = spark.table("ecommerce.customers");
        Dataset<Row> productsDF = spark.table("ecommerce.products");
        
        List<Integer> customerIds = new ArrayList<>();
        customersDF.select("customer_id").collectAsList()
            .forEach(row -> customerIds.add(row.getInt(0)));
        
        List<Integer> productIds = new ArrayList<>();
        List<Double> productPrices = new ArrayList<>();
        productsDF.select("product_id", "price").collectAsList()
            .forEach(row -> {
                productIds.add(row.getInt(0));
                productPrices.add(row.getDouble(1));
            });
        
        // Define schema
        StructType schema = new StructType(new StructField[]{
            DataTypes.createStructField("order_id", DataTypes.IntegerType, false),
            DataTypes.createStructField("customer_id", DataTypes.IntegerType, false),
            DataTypes.createStructField("product_id", DataTypes.IntegerType, false),
            DataTypes.createStructField("quantity", DataTypes.IntegerType, false),
            DataTypes.createStructField("total_amount", DataTypes.DoubleType, false),
            DataTypes.createStructField("status", DataTypes.StringType, false),
            DataTypes.createStructField("order_date", DataTypes.TimestampType, false)
        });
        
        // Generate data
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 11, 25);
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < numOrders; i++) {
            int orderId = i + 1;
            int customerId = customerIds.get(random.nextInt(customerIds.size()));
            int productIdx = random.nextInt(productIds.size());
            int productId = productIds.get(productIdx);
            double price = productPrices.get(productIdx);
            int quantity = 1 + random.nextInt(5);
            double totalAmount = Math.round(price * quantity * 100.0) / 100.0;
            String status = ORDER_STATUSES[random.nextInt(ORDER_STATUSES.length)];
            
            // Random date
            long randomDays = ThreadLocalRandom.current().nextLong(daysBetween);
            LocalDateTime orderDateTime = startDate.plusDays(randomDays).atStartOfDay();
            Timestamp orderDate = Timestamp.valueOf(orderDateTime);
            
            rows.add(RowFactory.create(orderId, customerId, productId, quantity, 
                totalAmount, status, orderDate));
        }
        
        Dataset<Row> ordersDF = spark.createDataFrame(rows, schema);
        
        // Write to Hive table as Parquet, partitioned by status
        ordersDF.write()
            .mode(SaveMode.Overwrite)
            .format("parquet")
            .partitionBy("status")
            .saveAsTable("ecommerce.orders");
        
        System.out.println("✓ Created orders table with " + numOrders + " records");
    }
    
    private static void printSampleData(SparkSession spark) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SAMPLE DATA");
        System.out.println("=".repeat(80));
        
        System.out.println("\n--- Customers (first 10) ---");
        spark.table("ecommerce.customers").show(10, false);
        
        System.out.println("\n--- Products (first 10) ---");
        spark.table("ecommerce.products").show(10, false);
        
        System.out.println("\n--- Orders (first 10) ---");
        spark.table("ecommerce.orders").show(10, false);
        
        System.out.println("\n--- Order Summary by Status ---");
        spark.sql(
            "SELECT status, " +
            "       COUNT(*) as order_count, " +
            "       ROUND(SUM(total_amount), 2) as total_revenue " +
            "FROM ecommerce.orders " +
            "GROUP BY status " +
            "ORDER BY order_count DESC"
        ).show();
        
        System.out.println("\n--- Top 10 Customers by Revenue ---");
        spark.sql(
            "SELECT c.customer_id, " +
            "       c.first_name, " +
            "       c.last_name, " +
            "       c.city, " +
            "       COUNT(o.order_id) as order_count, " +
            "       ROUND(SUM(o.total_amount), 2) as total_spent " +
            "FROM ecommerce.customers c " +
            "JOIN ecommerce.orders o ON c.customer_id = o.customer_id " +
            "GROUP BY c.customer_id, c.first_name, c.last_name, c.city " +
            "ORDER BY total_spent DESC " +
            "LIMIT 10"
        ).show(false);
    }
}
