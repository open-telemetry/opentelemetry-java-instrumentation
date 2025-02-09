/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_QUERY_IDEMPOTENT;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_MONGODB_COLLECTION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.HashMap;
import java.util.Map;

// until old database semconv are dropped in 3.0
public class SemconvStabilityUtil {

  private static final AttributeKey<String> DB_NAMESPACE = AttributeKey.stringKey("db.namespace");
  private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
  private static final AttributeKey<String> DB_OPERATION_NAME =
      AttributeKey.stringKey("db.operation.name");
  private static final AttributeKey<String> DB_COLLECTION_NAME =
      AttributeKey.stringKey("db.collection.name");

  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = buildMap();

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static Map<AttributeKey<?>, AttributeKey<?>> buildMap() {
    Map<AttributeKey<?>, AttributeKey<?>> map = new HashMap<>();
    map.put(DB_NAME, DB_NAMESPACE);
    map.put(DB_STATEMENT, DB_QUERY_TEXT);
    map.put(DB_OPERATION, DB_OPERATION_NAME);
    map.put(DB_SQL_TABLE, DB_COLLECTION_NAME);
    map.put(DB_CASSANDRA_TABLE, DB_COLLECTION_NAME);
    map.put(DB_MONGODB_COLLECTION, DB_COLLECTION_NAME);
    map.put(DB_SYSTEM, DB_SYSTEM_NAME);

    map.put(DB_CASSANDRA_CONSISTENCY_LEVEL, CASSANDRA_CONSISTENCY_LEVEL);
    map.put(DB_CASSANDRA_COORDINATOR_DC, CASSANDRA_COORDINATOR_DC);
    map.put(DB_CASSANDRA_COORDINATOR_ID, CASSANDRA_COORDINATOR_ID);
    map.put(DB_CASSANDRA_IDEMPOTENCE, CASSANDRA_QUERY_IDEMPOTENT);
    map.put(DB_CASSANDRA_PAGE_SIZE, CASSANDRA_PAGE_SIZE);
    map.put(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT, CASSANDRA_SPECULATIVE_EXECUTION_COUNT);

    return map;
  }

  private SemconvStabilityUtil() {}

  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> maybeStable(AttributeKey<T> oldKey) {
    // not testing database/dup
    if (SemconvStability.emitStableDatabaseSemconv()) {
      return (AttributeKey<T>) oldToNewMap.get(oldKey);
    }
    return oldKey;
  }

  public static String maybeStableDbSystemName(String oldDbSystemName) {
    // not testing database/dup
    if (SemconvStability.emitStableDatabaseSemconv()) {
      return SemconvStability.stableDbSystemName(oldDbSystemName);
    }
    return oldDbSystemName;
  }
}
