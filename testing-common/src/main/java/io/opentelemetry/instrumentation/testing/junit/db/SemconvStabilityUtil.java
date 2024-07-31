/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.HashMap;
import java.util.Map;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_COSMOSDB_CONTAINER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_MONGODB_COLLECTION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;

// until old database semconv are dropped in 3.0
public class SemconvStabilityUtil {
  private static final Map<AttributeKey<?>, AttributeKey<?>> oldToNewMap = new HashMap<>();
  private static final AttributeKey<String> DB_NAMESPACE = AttributeKey.stringKey("db.namespace");
  private static final AttributeKey<Long> DB_NAMESPACE_LONG = AttributeKey.longKey("db.namespace");
  private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
  private static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name");
  private static final AttributeKey<String> DB_COLLECTION_NAME = AttributeKey.stringKey("db.collection.name");

  static {
    addKey(oldToNewMap, DB_NAME, DB_NAMESPACE);
    addKey(oldToNewMap, DB_REDIS_DATABASE_INDEX, DB_NAMESPACE_LONG);
    addKey(oldToNewMap, DB_STATEMENT, DB_QUERY_TEXT);
    addKey(oldToNewMap, DB_OPERATION, DB_OPERATION_NAME);
    addKey(oldToNewMap, DB_SQL_TABLE, DB_COLLECTION_NAME);
    addKey(oldToNewMap, DB_CASSANDRA_TABLE, DB_COLLECTION_NAME);
    addKey(oldToNewMap, DB_MONGODB_COLLECTION, DB_COLLECTION_NAME);
    addKey(oldToNewMap, DB_COSMOSDB_CONTAINER, DB_COLLECTION_NAME);
  }

  private SemconvStabilityUtil() {}

  private static <T> void addKey(
      Map<AttributeKey<?>, AttributeKey<?>> map, AttributeKey<T> oldKey, AttributeKey<T> newKey) {
    map.put(oldKey, newKey);
  }

  @SuppressWarnings("unchecked")
  public static <T> AttributeKey<T> getAttributeKey(AttributeKey<T> oldKey) {
    if (SemconvStability.emitStableDatabaseSemconv()) {
      return (AttributeKey<T>) oldToNewMap.get(oldKey);
    }
    return oldKey;
  }
}
