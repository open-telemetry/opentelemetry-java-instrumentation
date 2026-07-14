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
    Long batchSize = extractBatchSize(operation, executionAttributes);
    WriteOperationType writeOpType =
        "BatchWriteItem".equals(operation)
            ? extractWriteOperationType(executionAttributes)
            : WriteOperationType.NONE;
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_OPERATION_NAME, getStableOperationName(operation, batchSize, writeOpType));
      if (shouldEmitBatchSize(batchSize)) {
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
  private static String getStableOperationName(
      @Nullable String operation, @Nullable Long batchSize, WriteOperationType writeOpType) {
    if ("BatchWriteItem".equals(operation)) {
      return getStableWriteOperationName(batchSize, writeOpType);
    }
    return operation;
  }

  private static String getStableWriteOperationName(
      @Nullable Long batchSize, WriteOperationType writeOpType) {
    if (batchSize == null || batchSize == 0 || writeOpType == WriteOperationType.NONE) {
      return "BatchWriteItem";
    }
    String itemOp = writeOpType == WriteOperationType.PUT ? "PutItem" : "DeleteItem";
    if (batchSize == 1) {
      return itemOp;
    }
    // mixed operations collapse to bare BATCH (consistent with SQL/Cassandra)
    if (writeOpType == WriteOperationType.MIXED) {
      return "BATCH";
    }
    return "BATCH " + itemOp;
  }

  @Nullable
  private static Long extractBatchSize(
      @Nullable String operation, ExecutionAttributes executionAttributes) {
    if (!"BatchWriteItem".equals(operation)) {
      return null;
    }

    SdkRequest request =
        executionAttributes.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
    if (request == null) {
      return null;
    }
    Optional<?> requestItems = request.getValueForField("RequestItems", Object.class);
    if (!requestItems.isPresent() || !(requestItems.get() instanceof Map)) {
      return null;
    }

    Map<?, ?> requestItemsMap = (Map<?, ?>) requestItems.get();
    return countBatchWriteItems(requestItemsMap);
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

  /**
   * Extracts the write operation type from a BatchWriteItem request. Returns PUT if all requests
   * are PutRequests, DELETE if all are DeleteRequests, MIXED if both types are present, or NONE if
   * the request is empty or cannot be inspected.
   */
  private WriteOperationType extractWriteOperationType(ExecutionAttributes executionAttributes) {
    SdkRequest request =
        executionAttributes.getAttribute(TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
    if (request == null) {
      return WriteOperationType.NONE;
    }
    Optional<?> requestItems = request.getValueForField("RequestItems", Object.class);
    if (!requestItems.isPresent() || !(requestItems.get() instanceof Map)) {
      return WriteOperationType.NONE;
    }

    WriteOperationType result = WriteOperationType.NONE;
    for (Object writeRequests : ((Map<?, ?>) requestItems.get()).values()) {
      if (writeRequests instanceof Collection) {
        for (Object writeRequest : (Collection<?>) writeRequests) {
          WriteOperationType opType = classifyWriteRequest(writeRequest);
          if (opType == WriteOperationType.NONE) {
            continue;
          }
          if (result == WriteOperationType.NONE) {
            result = opType;
          } else if (result != opType) {
            return WriteOperationType.MIXED;
          }
        }
      }
    }
    return result;
  }

  private WriteOperationType classifyWriteRequest(Object writeRequest) {
    // WriteRequest has putRequest() and deleteRequest() methods; exactly one returns non-null
    Object putRequest = next(writeRequest, "PutRequest");
    if (putRequest != null) {
      return WriteOperationType.PUT;
    }
    Object deleteRequest = next(writeRequest, "DeleteRequest");
    if (deleteRequest != null) {
      return WriteOperationType.DELETE;
    }
    return WriteOperationType.NONE;
  }

  // db.operation.batch.size is captured for every batch request (including an empty batch with
  // size 0); it is only omitted for a single-item batch, which is reported as a non-batch operation
  private static boolean shouldEmitBatchSize(@Nullable Long batchSize) {
    return batchSize != null && batchSize != 1;
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

  private enum WriteOperationType {
    NONE,
    PUT,
    DELETE,
    MIXED
  }
}
