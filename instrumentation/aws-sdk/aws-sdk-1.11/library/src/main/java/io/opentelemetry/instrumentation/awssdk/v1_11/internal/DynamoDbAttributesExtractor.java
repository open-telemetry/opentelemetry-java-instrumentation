/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

class DynamoDbAttributesExtractor implements AttributesExtractor<Request<?>, Response<?>> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
  // copied from AwsIncubatingAttributes
  private static final AttributeKey<List<String>> AWS_DYNAMODB_TABLE_NAMES =
      AttributeKey.stringArrayKey("aws.dynamodb.table_names");

  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String DYNAMODB = "dynamodb";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String AWS_DYNAMODB = "aws.dynamodb";

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_SYSTEM_NAME, AWS_DYNAMODB);
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_SYSTEM, DYNAMODB);
    }

    String operation = getOperationName(request.getOriginalRequest());
    Long batchSize = extractBatchSize(operation, request.getOriginalRequest());
    WriteOperationType writeOpType =
        "BatchWriteItem".equals(operation)
            ? extractWriteOperationType(request.getOriginalRequest())
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

    String tableName = RequestAccess.getTableName(request.getOriginalRequest());
    if (tableName != null) {
      attributes.put(AWS_DYNAMODB_TABLE_NAMES, singletonList(tableName));
    }
    if (emitStableDatabaseSemconv()) {
      attributes.put(
          DB_COLLECTION_NAME, getCollectionName(request.getOriginalRequest(), tableName));
    }
  }

  @Nullable
  private static String getCollectionName(Object request, @Nullable String tableName) {
    if (tableName != null) {
      return tableName;
    }

    Map<?, ?> requestItems = RequestAccess.getRequestItems(request);
    return requestItems != null ? getSingleCollectionName(requestItems) : null;
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
  private static Long extractBatchSize(@Nullable String operation, Object request) {
    if (!"BatchWriteItem".equals(operation)) {
      return null;
    }

    Map<?, ?> requestItems = RequestAccess.getRequestItems(request);
    if (requestItems == null) {
      return null;
    }

    long batchSize = countBatchWriteItems(requestItems);
    // return the size for every batch request, including an empty batch with size 0
    return batchSize;
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
  private static WriteOperationType extractWriteOperationType(Object request) {
    Map<?, ?> requestItems = RequestAccess.getRequestItems(request);
    if (requestItems == null) {
      return WriteOperationType.NONE;
    }

    WriteOperationType result = WriteOperationType.NONE;
    for (Object writeRequests : requestItems.values()) {
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

  private static WriteOperationType classifyWriteRequest(Object writeRequest) {
    // WriteRequest has getPutRequest() and getDeleteRequest() methods; exactly one returns non-null
    if (RequestAccess.hasPutRequest(writeRequest)) {
      return WriteOperationType.PUT;
    }
    if (RequestAccess.hasDeleteRequest(writeRequest)) {
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
  private static String getOperationName(Object request) {
    String name = request.getClass().getSimpleName();
    if (!name.endsWith("Request")) {
      return null;
    }

    return name.substring(0, name.length() - "Request".length());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {}

  private enum WriteOperationType {
    NONE,
    PUT,
    DELETE,
    MIXED
  }
}
