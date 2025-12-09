# Database Instrumentation - db.query.summary Audit

This document tracks the audit of all database instrumentations to ensure proper implementation and testing of `db.query.summary` attribute.

## Summary

### Key Findings

**Architecture:**
- SQL databases use `SqlClientAttributesExtractor` which **automatically computes** `db.query.summary` from SQL statements
- NoSQL databases use `DbClientAttributesExtractor` which requires manual implementation of `getDbQuerySummary()` in getters
- Higher-level abstractions (Hibernate, MyBatis, Spring Data) create **INTERNAL spans** with code attributes - they delegate to underlying DB for CLIENT spans

**Current Status:**
- ✅ SQL instrumentations (JDBC, R2DBC, Cassandra, Vertx SQL) - automatically get `db.query.summary` via `SqlClientAttributesExtractor`
- ✅ NoSQL instrumentations (MongoDB, Couchbase, Elasticsearch, ClickHouse, InfluxDB) - `getDbQuerySummary()` implemented
- ✅ Redis instrumentations (Redisson, Rediscala, Jedis, Lettuce) - `getDbQuerySummary()` implemented
- ➖ Higher-level abstractions (Hibernate, MyBatis, Spring Data) - N/A, create INTERNAL spans not DB CLIENT spans
- ➖ Connection pools (Apache DBCP, Vibur DBCP) - N/A, pool instrumentation not query instrumentation

**Remaining Work:**
- Span name verification for NoSQL/Redis instrumentations in stable semconv mode

## Checklist

For each instrumentation, check:
1. **Implementation**: Does it set `db.query.summary` when stable semconv is enabled?
2. **Tests**: Do tests assert `db.query.summary` attribute?
3. **Span Name**: Does span name use query summary format in stable semconv mode?

### SQL Databases (use SqlClientAttributesExtractor - auto db.query.summary)

- [x] **JDBC** (`instrumentation/jdbc`)
  - [x] Implementation sets db.query.summary (via SqlClientAttributesExtractor)
  - [x] Tests verify db.query.summary (AbstractJdbcInstrumentationTest)
  - [x] Span name follows stable semconv

- [x] **R2DBC** (`instrumentation/r2dbc-1.0`)
  - [x] Implementation sets db.query.summary (via SqlClientAttributesExtractor)
  - [x] Tests verify db.query.summary (AbstractR2dbcStatementTest)
  - [x] Span name follows stable semconv

- [x] **Cassandra** (`instrumentation/cassandra`)
  - [x] Implementation sets db.query.summary (via SqlClientAttributesExtractor)
  - [x] Tests verify db.query.summary (AbstractCassandraTest)
  - [x] Span name follows stable semconv

- [x] **Vertx SQL Client** (`instrumentation/vertx/vertx-sql-client`)
  - [x] Implementation sets db.query.summary (via SqlClientAttributesExtractor)
  - [x] Tests verify db.query.summary (VertxSqlClientTest)
  - [x] Span name follows stable semconv

### Higher-Level Abstractions (create INTERNAL spans, not DB CLIENT spans)

- [x] **Hibernate** (`instrumentation/hibernate`) - **N/A**
  - Creates INTERNAL spans for Hibernate operations
  - Actual DB CLIENT spans come from JDBC instrumentation
  - No db.query.summary needed (not a DB CLIENT span)

- [x] **MyBatis** (`instrumentation/mybatis-3.2`) - **N/A**
  - Creates INTERNAL spans with code attributes (SpanKindExtractor.alwaysInternal)
  - Actual DB CLIENT spans come from JDBC instrumentation
  - No db.query.summary needed (not a DB CLIENT span)

- [x] **Spring Data** (`instrumentation/spring/spring-data`) - **N/A**
  - Creates INTERNAL spans with code attributes
  - Actual DB CLIENT spans come from underlying DB instrumentation
  - No db.query.summary needed (not a DB CLIENT span)

### Connection Pools (not query instrumentation)

- [x] **Apache DBCP** (`instrumentation/apache-dbcp-2.0`) - **N/A**
  - Connection pool instrumentation
  - Does not create DB query spans

- [x] **Vibur DBCP** (`instrumentation/vibur-dbcp-11.0`) - **N/A**
  - Connection pool instrumentation
  - Does not create DB query spans

### NoSQL Databases (use DbClientAttributesExtractor - needs manual implementation)

- [x] **MongoDB** (`instrumentation/mongo`)
  - [x] Implementation sets db.query.summary (MongoDbAttributesGetter.getDbQuerySummary)
  - [x] Tests verify db.query.summary (AbstractMongoClientTest)
  - [x] Span name follows stable semconv

- [x] **Couchbase** (`instrumentation/couchbase`)
  - [x] Implementation sets db.query.summary (CouchbaseAttributesGetter.getDbQuerySummary)
  - [x] Tests verify db.query.summary (AbstractCouchbaseTest.assertCouchbaseSpan)
  - [ ] Span name follows stable semconv

- [x] **Elasticsearch** (`instrumentation/elasticsearch`)
  - [x] Implementation sets db.query.summary (ElasticsearchDbAttributesGetter & ElasticsearchTransportAttributesGetter)
  - [x] Tests verify db.query.summary (AbstractElasticsearchNodeClientTest)
  - [ ] Span name follows stable semconv

- [x] **ClickHouse** (`instrumentation/clickhouse`)
  - [x] Implementation sets db.query.summary (ClickHouseAttributesGetter.getDbQuerySummary)
  - [x] Tests verify db.query.summary (ClickHouseClientV1Test, ClickHouseClientV2Test)
  - [ ] Span name follows stable semconv

- [x] **InfluxDB** (`instrumentation/influxdb-2.4`)
  - [x] Implementation sets db.query.summary (InfluxDbAttributesGetter.getDbQuerySummary)
  - [x] Tests verify db.query.summary (InfluxDbClientTest)
  - [ ] Span name follows stable semconv

### Redis (use DbClientAttributesExtractor - needs manual implementation)

- [x] **Redisson** (`instrumentation/redisson`)
  - [x] Implementation sets db.query.summary (RedissonDbAttributesGetter.getDbQuerySummary)
  - [x] Tests verify db.query.summary (AbstractRedissonClientTest, AbstractRedissonAsyncClientTest)
  - [ ] Span name follows stable semconv

- [x] **Rediscala** (`instrumentation/rediscala-1.8`)
  - [x] Implementation sets db.query.summary (RediscalaAttributesGetter.getDbQuerySummary)
  - [x] Tests verify db.query.summary (RediscalaClientTest.redisSpanAttributes)
  - [ ] Span name follows stable semconv

- [x] **Jedis** (`instrumentation/jedis`)
  - [x] Implementation sets db.query.summary (JedisDbAttributesGetter.getDbQuerySummary for v1.4, v3.0, v4.0)
  - [x] Tests verify db.query.summary (AbstractJedisTest, Jedis30ClientTest, Jedis40ClientTest)
  - [ ] Span name follows stable semconv

- [x] **Lettuce** (`instrumentation/lettuce`)
  - [x] Implementation sets db.query.summary (LettuceDbAttributesGetter.getDbQuerySummary for v4.0, v5.0)
  - [x] Tests verify db.query.summary (LettuceSyncClientTest, LettuceAsyncClientTest, LettuceReactiveClientTest)
  - [ ] Span name follows stable semconv

---

## Implementation Pattern

### SQL Databases (Automatic via SqlClientAttributesExtractor)

For SQL databases, `db.query.summary` is **automatically computed** by `SqlClientAttributesExtractor`:
- Parses SQL statement to extract operation and table
- Sets `db.query.summary` = `"<operation> <table>"` (e.g., `"SELECT users"`)
- No additional implementation needed!

### NoSQL Databases (Manual Implementation Required)

NoSQL databases use `DbClientAttributesExtractor` which reads `db.query.summary` from the getter:

```java
// In the attributes getter class (e.g., MongoDbAttributesGetter)
@Override
public String getDbQuerySummary(REQUEST request) {
  // Return format: "<operation> <collection>"
  // e.g., "find users", "insert orders", "GET key"
  String operation = getDbOperationName(request);
  String collection = getDbCollectionName(request);
  if (operation != null && collection != null) {
    return operation + " " + collection;
  }
  return operation;
}
```

### Test Pattern

```java
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

// Helper method
private static String querySummaryOrNull(String operation, String collection) {
  return emitStableDatabaseSemconv() ? operation + " " + collection : null;
}

// In test assertions:
equalTo(DB_QUERY_SUMMARY, querySummaryOrNull("SELECT", "users"))
```

### Span Name Pattern

- **Stable semconv**: `"<operation> <collection>"` (e.g., `"SELECT users"`, `"find orders"`)
- **Old semconv**: `"<operation> <namespace>.<collection>"` (e.g., `"SELECT mydb.users"`)

---

## Next Steps

1. **Add db.query.summary tests to Cassandra** - implementation exists but tests don't verify it
2. **Implement getDbQuerySummary for NoSQL databases:**
   - MongoDB - `"<command> <collection>"`
   - Couchbase - `"<operation>"` (no collection concept)
   - Elasticsearch - `"<method> <index>"`
   - ClickHouse - SQL-based, may need custom handling
   - InfluxDB - `"<operation>"`
3. **Implement getDbQuerySummary for Redis:**
   - Redisson - `"<command>"`
   - Rediscala - `"<command>"`

---

## Commands

```bash
# Check implementation for db.query.summary usage
grep -r "DB_QUERY_SUMMARY\|getDbQuerySummary" instrumentation/<name>/ --include="*.java"

# Check which extractor is used
grep -r "SqlClientAttributesExtractor\|DbClientAttributesExtractor" instrumentation/<name>/ --include="*.java"

# Run stable semconv tests
./gradlew :instrumentation:<path>:testStableSemconv

# Run regular tests
./gradlew :instrumentation:<path>:test
```

## Architecture Reference

### SqlClientAttributesExtractor
- Used by: JDBC, R2DBC, Cassandra, Vertx SQL Client
- Automatically computes `db.query.summary` from SQL via `SqlStatementSanitizerUtil`
- No manual implementation needed

### DbClientAttributesExtractor
- Used by: MongoDB, Couchbase, Elasticsearch, ClickHouse, InfluxDB, Redisson, Rediscala
- Reads `db.query.summary` from `DbClientAttributesGetter.getDbQuerySummary()`
- Default implementation returns `null` - must override to provide value

### DbClientSpanNameExtractor
- For SQL: Uses query summary as span name in stable semconv
- For NoSQL: Uses `"<operation> <namespace>"` format (doesn't use query summary for span name)
