/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

class DynamoDbAttributesExtractor implements AttributesExtractor<ExecutionAttributes, Response> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");

  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String DYNAMODB = "dynamodb";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String AWS_DYNAMODB = "aws.dynamodb";

  private final MethodHandleFactory methodHandleFactory = new MethodHandleFactory();

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ExecutionAttributes executionAttributes) {
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_SYSTEM_NAME, AWS_DYNAMODB);
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_SYSTEM, DYNAMODB);
    }
    String operation = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    long batchSize = extractBatchSize(operation, executionAttributes);
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_OPERATION_NAME, getStableOperationName(operation, batchSize));
      if (isBatch(batchSize)) {
        attributes.put(DB_OPERATION_BATCH_SIZE, batchSize);
      }
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_OPERATION, operation);
    }
    if (emitStableDatabaseSemconv()) {
      String tableName = extractTableName(executionAttributes);
      if (tableName != null) {
        attributes.put(DB_COLLECTION_NAME, tableName);
      }
    }
  }

  @Nullable
  private static String getStableOperationName(@Nullable String operation, long batchSize) {
    if ("BatchGetItem".equals(operation)) {
      return isBatch(batchSize) ? "BATCH GetItem" : "GetItem";
    }
    if ("BatchWriteItem".equals(operation)) {
      return isBatch(batchSize) ? "BATCH WriteItem" : "WriteItem";
    }
    return operation;
  }

  private long extractBatchSize(
      @Nullable String operation, ExecutionAttributes executionAttributes) {
    if (!"BatchGetItem".equals(operation) && !"BatchWriteItem".equals(operation)) {
      return 0;
    }

    SdkRequest request =
        executionAttributes.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
    if (request == null) {
      return 0;
    }
    Optional<?> requestItems = request.getValueForField("RequestItems", Object.class);
    if (!requestItems.isPresent() || !(requestItems.get() instanceof Map)) {
      return 0;
    }

    Map<?, ?> requestItemsMap = (Map<?, ?>) requestItems.get();
    return "BatchGetItem".equals(operation)
        ? countBatchGetItems(requestItemsMap)
        : countBatchWriteItems(requestItemsMap);
  }

  private long countBatchGetItems(Map<?, ?> requestItems) {
    long count = 0;
    for (Object keysAndAttributes : requestItems.values()) {
      Object keys = next(keysAndAttributes, "Keys");
      if (keys instanceof Collection) {
        count += ((Collection<?>) keys).size();
      }
    }
    return count;
  }

  private static long countBatchWriteItems(Map<?, ?> requestItems) {
    long count = 0;
    for (Object writeRequests : requestItems.values()) {
      if (writeRequests instanceof Collection) {
        count += ((Collection<?>) writeRequests).size();
      }
    }
    return count;
  }

  private static boolean isBatch(long batchSize) {
    return batchSize > 1;
  }

  @Nullable
  private Object next(Object current, String fieldName) {
    try {
      return methodHandleFactory.forField(current.getClass(), fieldName).invoke(current);
    } catch (Throwable ignored) {
      return null;
    }
  }

  @Nullable
  private static String extractTableName(ExecutionAttributes executionAttributes) {
    SdkRequest request =
        executionAttributes.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
    if (request == null) {
      return null;
    }
    // Single-table operations expose TableName directly.
    Optional<String> tableName = request.getValueForField("TableName", String.class);
    if (tableName.isPresent() && !tableName.get().isEmpty()) {
      return tableName.get();
    }
    // Batch operations (BatchGetItem, BatchWriteItem) key RequestItems by table name.
    // Emit db.collection.name only when the batch targets exactly one table; omit it otherwise,
    // as the attribute is defined as a single collection identifier.
    Optional<?> requestItems = request.getValueForField("RequestItems", Object.class);
    if (requestItems.isPresent() && requestItems.get() instanceof Map) {
      return getSingleCollectionName((Map<?, ?>) requestItems.get());
    }
    return null;
  }

  @Nullable
  private static String getSingleCollectionName(Map<?, ?> requestItems) {
    String collectionName = null;
    for (Object candidate : requestItems.keySet()) {
      if (!(candidate instanceof String)) {
        return null;
      }
      if (collectionName != null && !collectionName.equals(candidate)) {
        return null;
      }
      collectionName = (String) candidate;
    }
    return collectionName;
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {}
}
