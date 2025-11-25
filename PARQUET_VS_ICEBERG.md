# Parquet vs Iceberg: Comparison Guide

This lakehouse demo supports **both** Parquet and Iceberg table formats. You can use either or both simultaneously!

## Quick Reference

### Parquet (Hive) Approach
- **Catalog**: `hive` 
- **Scripts**: 
  - `./run_spark_app.sh` (Python)
  - `./run_spark_app_java.sh` (Java)
- **Tables**: `hive.ecommerce.*`
- **Storage**: `s3a://warehouse/ecommerce.db/`

### Iceberg Approach
- **Catalog**: `iceberg`
- **Scripts**:
  - `./run_iceberg_spark_app.sh` (Python)
  - `./run_iceberg_spark_app_java.sh` (Java)
- **Tables**: `iceberg.ecommerce.*`
- **Storage**: `s3a://iceberg/warehouse/ecommerce.db/`

## Are Old Parquet Scripts Still Usable?

**YES!** Both approaches work simultaneously:

### What Changed
- **Hive Metastore**: Upgraded from file-based to HMS (Hive Metastore Service)
  - This is actually an **improvement** for Parquet tables too
  - Better performance, concurrent access, more reliable

### What Stayed the Same
- All old scripts still work
- Same Python and Java code
- Same table structures
- Same query patterns

### Side-by-Side Example

```bash
# Generate Parquet tables (old way, still works)
./run_spark_app.sh

# Generate Iceberg tables (new way)
./run_iceberg_spark_app.sh

# Query both in Trino
docker exec -it trino trino

# Parquet tables
USE hive.ecommerce;
SELECT * FROM customers LIMIT 10;

# Iceberg tables  
USE iceberg.ecommerce;
SELECT * FROM customers LIMIT 10;
```

## Feature Comparison

| Feature | Parquet (Hive) | Iceberg |
|---------|---------------|---------|
| **ACID Transactions** | ❌ No | ✅ Yes |
| **Time Travel** | ❌ No | ✅ Yes (snapshots) |
| **Schema Evolution** | ⚠️ Limited | ✅ Full support |
| **Partition Pruning** | ✅ Manual | ✅ Hidden/Automatic |
| **Concurrent Writes** | ⚠️ Conflicts | ✅ Safe |
| **Metadata Management** | ⚠️ Expensive | ✅ Efficient |
| **UPDATE/DELETE** | ❌ No | ✅ Yes |
| **Small File Problem** | ⚠️ Manual compaction | ✅ Built-in compaction |
| **Setup Complexity** | ✅ Simple | ⚠️ Requires HMS |
| **Query Performance** | ✅ Good | ✅ Better (with metadata pruning) |

## When to Use Each

### Use Parquet (Hive) When:
- ✅ You need simple, append-only data pipelines
- ✅ You're working with immutable datasets
- ✅ You want minimal infrastructure
- ✅ Direct file access is important
- ✅ You're migrating from existing Hive tables

### Use Iceberg When:
- ✅ You need ACID guarantees
- ✅ You want to UPDATE/DELETE data
- ✅ Schema changes are frequent
- ✅ Multiple writers access the same tables
- ✅ You need time travel/audit capabilities
- ✅ Building a data lakehouse (not just data lake)

## Architecture Differences

### Parquet (Hive) Architecture
```
Spark → Parquet Files → MinIO (s3a://warehouse/)
                ↓
        Hive Metastore (table metadata)
                ↓
        Trino (queries via Hive connector)
```

**Metadata**: 
- Table schema stored in HMS
- Partition info in HMS
- Direct file listings for queries

### Iceberg Architecture
```
Spark → Iceberg Tables → MinIO (s3a://iceberg/warehouse/)
         ↓
    Metadata Files (manifests, snapshots)
         ↓
    Hive Metastore (table pointers)
         ↓
    Trino (queries via Iceberg connector)
```

**Metadata**:
- Table schema in metadata files
- Snapshot history in metadata files
- HMS stores only table location
- Manifest files enable fast metadata pruning

## Storage Layout

### Parquet
```
s3a://warehouse/ecommerce.db/
├── customers/
│   ├── part-00000.parquet
│   └── part-00001.parquet
├── products/
│   └── part-00000.parquet
└── orders/
    ├── status=pending/part-00000.parquet
    ├── status=shipped/part-00000.parquet
    └── status=delivered/part-00000.parquet
```

### Iceberg
```
s3a://iceberg/warehouse/ecommerce.db/
├── customers/
│   ├── data/
│   │   └── 00000-0-xxx.parquet
│   └── metadata/
│       ├── v1.metadata.json
│       ├── v2.metadata.json
│       └── snap-xxx.avro (manifest list)
└── orders/
    ├── data/
    │   └── 00000-0-xxx.parquet (Iceberg handles partitioning)
    └── metadata/
        ├── v1.metadata.json
        └── snap-xxx.avro
```

## Migration Path

### From Parquet to Iceberg

You don't need to migrate! Both can coexist. But if you want to:

```sql
-- Option 1: Create new Iceberg table from Parquet
CREATE TABLE iceberg.ecommerce.customers_v2
WITH (format='PARQUET', partitioning=ARRAY['state'])
AS SELECT * FROM hive.ecommerce.customers;

-- Option 2: In-place migration (using Spark)
CALL iceberg.system.migrate('hive.ecommerce.customers');
```

### Dual Write Pattern

Write to both formats during transition:

```python
# Write to Parquet
df.write.mode("append").saveAsTable("hive.ecommerce.customers")

# Also write to Iceberg
df.writeTo("iceberg.ecommerce.customers").using("iceberg").append()
```

## Query Pattern Differences

### Parquet Queries
```sql
-- Need to specify partition filter for performance
SELECT * FROM hive.ecommerce.orders 
WHERE status = 'delivered';  -- Partition pruning applied

-- No time travel
SELECT * FROM hive.ecommerce.customers;  -- Always latest
```

### Iceberg Queries
```sql
-- Automatic partition pruning (hidden partitioning)
SELECT * FROM iceberg.ecommerce.orders 
WHERE status = 'delivered';  -- Users don't need to know it's partitioned

-- Time travel
SELECT * FROM iceberg.ecommerce.customers 
FOR VERSION AS OF 1234567890123456789;  -- Query historical snapshot

-- Metadata queries
SELECT * FROM "iceberg.ecommerce.orders$snapshots";
SELECT * FROM "iceberg.ecommerce.orders$history";
SELECT * FROM "iceberg.ecommerce.orders$files";
```

## Performance Comparison

### For Reads
- **Parquet**: Fast for full scans, manual partition filters needed
- **Iceberg**: Fast with automatic metadata pruning, hidden partitioning

### For Writes
- **Parquet**: Fast appends, no coordination needed (but watch for conflicts)
- **Iceberg**: Slightly slower (metadata updates), but safe concurrent writes

### For Updates/Deletes
- **Parquet**: Not supported (need rewrite entire table/partition)
- **Iceberg**: Native support with copy-on-write or merge-on-read

## Cost Considerations

### Storage
- **Parquet**: Data files only
- **Iceberg**: Data files + metadata files + manifests (~1-5% overhead)

### Compute
- **Parquet**: Lower for simple queries
- **Iceberg**: Can be lower for complex queries (better pruning)

### Operations
- **Parquet**: Manual maintenance (compaction, cleanup)
- **Iceberg**: Built-in maintenance procedures

## Recommendations

### Start with Parquet If:
1. You're learning lakehouse concepts
2. You have simple batch ETL
3. You rarely update data
4. You want minimal complexity

### Start with Iceberg If:
1. Building production lakehouse
2. Need data quality guarantees (ACID)
3. Multiple teams writing data
4. Frequent schema changes
5. Need audit/compliance (time travel)

### Hybrid Approach:
1. Use Parquet for immutable reference data
2. Use Iceberg for transactional tables
3. Keep both catalogs active
4. Migrate table-by-table based on needs

## Summary

Both approaches are **fully supported** in this demo. The old Parquet scripts still work perfectly—we just upgraded the metastore infrastructure which benefits both formats.

**Key Takeaway**: You can use both Parquet and Iceberg tables side-by-side. Start with what you know, migrate when you need advanced features.
