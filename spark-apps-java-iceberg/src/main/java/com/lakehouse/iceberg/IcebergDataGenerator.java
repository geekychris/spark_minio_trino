package com.lakehouse.iceberg;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.*;
import scala.collection.JavaConverters;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Iceberg Data Generator
 * <p>
 * Generates sample e-commerce data and writes it to Apache Iceberg tables in MinIO.
 * Configuration is passed via spark-submit command line.
 */
public class IcebergDataGenerator {

    private static final String[] FIRST_NAMES = {
            "John", "Jane", "Bob", "Alice", "Chris", "Christine", "Eve", "Frank", "Grace", "Henry"
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

    private static final Map<String, String[]> PRODUCT_NAMES = new HashMap<String, String[]>() {{
        put("Electronics", new String[]{"Laptop", "Smartphone", "Tablet", "Headphones", "Smart Watch"});
        put("Clothing", new String[]{"T-Shirt", "Jeans", "Jacket", "Sneakers", "Dress"});
        put("Home & Garden", new String[]{"Coffee Maker", "Lamp", "Plant Pot", "Pillow", "Rug"});
        put("Sports", new String[]{"Basketball", "Tennis Racket", "Yoga Mat", "Dumbbells", "Running Shoes"});
        put("Books", new String[]{"Novel", "Cookbook", "Biography", "Self-Help", "Mystery"});
        put("Toys", new String[]{"Action Figure", "Board Game", "Puzzle", "Doll", "Building Blocks"});
        put("Food", new String[]{"Coffee Beans", "Chocolate", "Snack Mix", "Pasta", "Olive Oil"});
        put("Health", new String[]{"Vitamins", "Protein Powder", "Face Cream", "Shampoo", "Toothpaste"});
    }};

    private static final String[] PRODUCT_MODIFIERS = {
            "Pro", "Premium", "Classic", "Deluxe", "Standard"
    };

    private static final String[] ORDER_STATUSES = {
            "pending", "shipped", "delivered", "cancelled"
    };

    private final Random random = new Random();
    private final SparkSession spark;

    public IcebergDataGenerator(SparkSession spark) {
        this.spark = spark;
    }

    public static void main(String[] args) {
        System.out.println("Starting Iceberg Data Generation...");
        System.out.println("================================================================================");

        // Create Spark session - configuration passed via spark-submit
        SparkSession spark = SparkSession.builder()
                .appName("IcebergDataGenerator")
                .getOrCreate();

        try {
            IcebergDataGenerator generator = new IcebergDataGenerator(spark);

            // Generate data
            generator.generateCustomers(1000);
            generator.generateProducts(100);
            generator.generateOrders(5000);

            // Print sample data
            generator.printSampleData();

            System.out.println("\n================================================================================");
            System.out.println("✓ Iceberg data generation completed successfully!");
            System.out.println("================================================================================");
            System.out.println("\nYou can now query the data using Trino:");
            System.out.println("  docker exec -it trino trino");
            System.out.println("  trino> SHOW SCHEMAS IN iceberg;");
            System.out.println("  trino> USE iceberg.ecommerce;");
            System.out.println("  trino> SHOW TABLES;");
            System.out.println("  trino> SELECT * FROM customers LIMIT 10;");
            System.out.println("\nIceberg-specific features:");
            System.out.println("  trino> SELECT * FROM customers FOR VERSION AS OF <snapshot-id>;  -- Time travel");
            System.out.println("  trino> SELECT * FROM \"customers$history\";  -- View table history");
            System.out.println("  trino> SELECT * FROM \"customers$snapshots\";  -- View snapshots");

        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            spark.stop();
        }
    }

    private void generateCustomers(int numCustomers) {
        System.out.println("Generating " + numCustomers + " customers...");

        // Create database if not exists
        try {
            spark.sql("CREATE DATABASE IF NOT EXISTS iceberg.ecommerce");
        } catch (Exception e) {
            System.out.println("Note: Database might already exist - continuing: " + e.getMessage());
        }

        // Generate customer data
        List<Row> customersData = new ArrayList<>();
        for (int i = 1; i <= numCustomers; i++) {
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String email = String.format("%s.%s%d@example.com",
                    firstName.toLowerCase(), lastName.toLowerCase(), i);
            int cityIdx = random.nextInt(CITIES.length);
            String city = CITIES[cityIdx];
            String state = STATES[cityIdx];

            customersData.add(RowFactory.create(i, firstName, lastName, email, city, state));
        }

        // Define schema
        StructType schema = new StructType(new StructField[]{
                new StructField("customer_id", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("first_name", DataTypes.StringType, false, Metadata.empty()),
                new StructField("last_name", DataTypes.StringType, false, Metadata.empty()),
                new StructField("email", DataTypes.StringType, false, Metadata.empty()),
                new StructField("city", DataTypes.StringType, false, Metadata.empty()),
                new StructField("state", DataTypes.StringType, false, Metadata.empty())
        });

        // Create DataFrame
        Dataset<Row> customersDF = spark.createDataFrame(customersData, schema);

        // Write to Iceberg table
        customersDF.writeTo("iceberg.ecommerce.customers")
                .using("iceberg")
                .tableProperty("format-version", "2")
                .createOrReplace();

        System.out.println("✓ Created Iceberg customers table with " + numCustomers + " records");
    }

    private void generateProducts(int numProducts) {
        System.out.println("Generating " + numProducts + " products...");

        List<Row> productsData = new ArrayList<>();
        for (int i = 1; i <= numProducts; i++) {
            String category = CATEGORIES[random.nextInt(CATEGORIES.length)];
            String[] names = PRODUCT_NAMES.get(category);
            String name = names[random.nextInt(names.length)];
            String modifier = PRODUCT_MODIFIERS[random.nextInt(PRODUCT_MODIFIERS.length)];
            String fullName = name + " " + modifier;
            double price = Math.round((9.99 + random.nextDouble() * 990.0) * 100.0) / 100.0;

            productsData.add(RowFactory.create(i, fullName, category, price));
        }

        StructType schema = new StructType(new StructField[]{
                new StructField("product_id", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("name", DataTypes.StringType, false, Metadata.empty()),
                new StructField("category", DataTypes.StringType, false, Metadata.empty()),
                new StructField("price", DataTypes.DoubleType, false, Metadata.empty())
        });

        Dataset<Row> productsDF = spark.createDataFrame(productsData, schema);

        productsDF.writeTo("iceberg.ecommerce.products")
                .using("iceberg")
                .tableProperty("format-version", "2")
                .createOrReplace();

        System.out.println("✓ Created Iceberg products table with " + numProducts + " records");
    }

    private void generateOrders(int numOrders) {
        System.out.println("Generating " + numOrders + " orders...");

        // Read existing customers and products
        Dataset<Row> customersDF = spark.table("iceberg.ecommerce.customers");
        Dataset<Row> productsDF = spark.table("iceberg.ecommerce.products");

        List<Integer> customerIds = new ArrayList<>();
        customersDF.select("customer_id").collectAsList()
                .forEach(row -> customerIds.add(row.getInt(0)));

        Map<Integer, Double> productPrices = new HashMap<>();
        productsDF.select("product_id", "price").collectAsList()
                .forEach(row -> productPrices.put(row.getInt(0), row.getDouble(1)));
        List<Integer> productIds = new ArrayList<>(productPrices.keySet());

        // Generate order data
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 11, 25);
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);

        List<Row> ordersData = new ArrayList<>();
        for (int i = 1; i <= numOrders; i++) {
            int customerId = customerIds.get(random.nextInt(customerIds.size()));
            int productId = productIds.get(random.nextInt(productIds.size()));
            int quantity = random.nextInt(5) + 1;
            double price = productPrices.get(productId);
            double totalAmount = Math.round(price * quantity * 100.0) / 100.0;
            String status = ORDER_STATUSES[random.nextInt(ORDER_STATUSES.length)];

            // Random date
            long randomDays = ThreadLocalRandom.current().nextLong(daysBetween);
            LocalDateTime orderDate = startDate.plusDays(randomDays).atStartOfDay();
            Timestamp orderTimestamp = Timestamp.valueOf(orderDate);

            ordersData.add(RowFactory.create(
                    i, customerId, productId, quantity, totalAmount, orderTimestamp, status
            ));
        }

        StructType schema = new StructType(new StructField[]{
                new StructField("order_id", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("customer_id", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("product_id", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("quantity", DataTypes.IntegerType, false, Metadata.empty()),
                new StructField("total_amount", DataTypes.DoubleType, false, Metadata.empty()),
                new StructField("order_date", DataTypes.TimestampType, false, Metadata.empty()),
                new StructField("status", DataTypes.StringType, false, Metadata.empty())
        });

        Dataset<Row> ordersDF = spark.createDataFrame(ordersData, schema);

        // Write to Iceberg table with partitioning by status
        org.apache.spark.sql.Column[] partCols = new org.apache.spark.sql.Column[]{};
        ordersDF.writeTo("iceberg.ecommerce.orders")
                .using("iceberg")
                .tableProperty("format-version", "2")
                .partitionedBy(functions.col("status"), JavaConverters.asScalaBuffer(java.util.Arrays.asList(partCols)).toSeq())
                .createOrReplace();

        System.out.println("✓ Created Iceberg orders table with " + numOrders + " records (partitioned by status)");
    }

    private void printSampleData() {
        System.out.println("\n================================================================================");
        System.out.println("SAMPLE DATA");
        System.out.println("================================================================================");

        System.out.println("\n--- Customers (first 10) ---");
        spark.table("iceberg.ecommerce.customers").show(10, false);

        System.out.println("\n--- Products (first 10) ---");
        spark.table("iceberg.ecommerce.products").show(10, false);

        System.out.println("\n--- Orders (first 10) ---");
        spark.table("iceberg.ecommerce.orders").show(10, false);

        System.out.println("\n--- Order Summary by Status ---");
        spark.sql("SELECT status, COUNT(*) as order_count, ROUND(SUM(total_amount), 2) as total_revenue " +
                "FROM iceberg.ecommerce.orders GROUP BY status ORDER BY order_count DESC").show();

        System.out.println("\n--- Top 10 Customers by Revenue ---");
        spark.sql("SELECT c.customer_id, c.first_name, c.last_name, c.city, " +
                "COUNT(o.order_id) as order_count, ROUND(SUM(o.total_amount), 2) as total_spent " +
                "FROM iceberg.ecommerce.customers c " +
                "JOIN iceberg.ecommerce.orders o ON c.customer_id = o.customer_id " +
                "GROUP BY c.customer_id, c.first_name, c.last_name, c.city " +
                "ORDER BY total_spent DESC LIMIT 10").show(false);

        // Show Iceberg metadata
        System.out.println("\n--- Iceberg Table Metadata ---");
        System.out.println("\nOrders table history:");
        spark.sql("SELECT * FROM iceberg.ecommerce.orders.history").show(false);

        System.out.println("\nOrders table snapshots:");
        spark.sql("SELECT * FROM iceberg.ecommerce.orders.snapshots").show(false);
    }
}
