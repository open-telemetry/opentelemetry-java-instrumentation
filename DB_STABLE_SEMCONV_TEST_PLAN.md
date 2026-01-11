# Plan: Fix Remaining Database Instrumentation Stable Semconv Tests

## Overview

Apply the same `db.query.summary` pattern used for JDBC to all other SQL-based database instrumentations.

## Pattern to Apply

For each test that validates span names/attributes in stable semconv mode:

```java
// 1. Span name: use querySummary instead of old format
span.hasName(emitStableDatabaseSemconv() ? querySummary(operation, table) : oldSpanName)

// 2. Add DB_QUERY_SUMMARY attribute assertion
equalTo(DB_QUERY_SUMMARY, emitStableDatabaseSemconv() ? querySummary(operation, table) : null)
```

## Instrumentations to Check

### 1. R2DBC (Reactive Relational Database Connectivity)

- **Location:** `instrumentation/r2dbc-1.0/`
- **Test command:** `./gradlew :instrumentation:r2dbc-1.0:javaagent:testStableSemconv --fail-fast`
- **Expected changes:** Similar to JDBC - update span name expectations and add `DB_QUERY_SUMMARY` assertions

### 2. Hibernate

- **Location:** `instrumentation/hibernate/`
- **Test command:** `./gradlew :instrumentation:hibernate:hibernate-6.0:javaagent:testStableSemconv --fail-fast`
- **Note:** May generate SQL queries internally - check if tests validate SQL spans

### 3. jOOQ

- **Location:** `instrumentation/jooq-3.0/`
- **Test command:** `./gradlew :instrumentation:jooq-3.0:javaagent:testStableSemconv --fail-fast`

### 4. Spring Data

- **Location:** `instrumentation/spring/spring-data/`
- **Test command:** `./gradlew :instrumentation:spring:spring-data:spring-data-3.0:testing:testStableSemconv --fail-fast`

### 5. MyBatis

- **Location:** `instrumentation/mybatis-3.2/`
- **Test command:** `./gradlew :instrumentation:mybatis-3.2:javaagent:testStableSemconv --fail-fast`

### 6. Vertx SQL Client

- **Location:** `instrumentation/vertx/`
- **Test command:** `./gradlew :instrumentation:vertx:vertx-sql-client-4.0:javaagent:testStableSemconv --fail-fast`

### 7. OpenTelemetry Extension Annotations (with SQL)

- **Location:** `instrumentation/opentelemetry-extension-annotations-1.0/`

## Execution Steps

1. **Run each test suite** with `--fail-fast` to identify failures
2. **For each failing test:**
   - Add `querySummary()` helper if not present
   - Update span name assertion to be conditional on `emitStableDatabaseSemconv()`
   - Add `DB_QUERY_SUMMARY` attribute assertion
3. **Verify both modes pass:**
   - `./gradlew :<module>:test` (old semconv)
   - `./gradlew :<module>:testStableSemconv` (stable semconv)

## Quick Discovery Command

Find all `testStableSemconv` tasks:

```bash
./gradlew tasks --all | grep -i "testStableSemconv"
```

Or check which modules have stable semconv test configurations:

```bash
grep -r "testStableSemconv" --include="*.gradle.kts" instrumentation/
```

## Priority Order

1. **R2DBC** - Most similar to JDBC, likely same patterns
2. **Vertx SQL Client** - Direct SQL execution
3. **jOOQ** - SQL generation library
4. **Hibernate/Spring Data/MyBatis** - ORM layers (may just use underlying JDBC spans)

## Notes

- Some instrumentations may not have their own SQL span tests if they rely on JDBC instrumentation underneath
- Focus on instrumentations that directly parse/execute SQL and emit their own spans
- The `querySummary()` helper may need to be added to each test class (or a shared test utility)
