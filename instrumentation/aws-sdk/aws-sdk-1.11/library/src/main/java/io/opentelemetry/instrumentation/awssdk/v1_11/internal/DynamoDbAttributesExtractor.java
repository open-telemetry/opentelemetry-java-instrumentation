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
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_OPERATION_NAME, getStableOperationName(operation, batchSize));
      if (isBatch(batchSize)) {
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
      @Nullable String operation, @Nullable Long batchSize) {
    if ("BatchGetItem".equals(operation)) {
      return getStableBatchOperationName(batchSize, "GetItem", operation);
    }
    if ("BatchWriteItem".equals(operation)) {
      return getStableBatchOperationName(batchSize, "WriteItem", operation);
    }
    return operation;
  }

  private static String getStableBatchOperationName(
      @Nullable Long batchSize, String itemOperation, String batchOperation) {
    if (batchSize == null || batchSize == 0) {
      return batchOperation;
    }
    if (batchSize == 1) {
      return itemOperation;
    }
    return "BATCH " + itemOperation;
  }

  @Nullable
  private static Long extractBatchSize(@Nullable String operation, Object request) {
    if (!"BatchGetItem".equals(operation) && !"BatchWriteItem".equals(operation)) {
      return null;
    }

    Map<?, ?> requestItems = RequestAccess.getRequestItems(request);
    if (requestItems == null) {
      return null;
    }

    long batchSize =
        "BatchGetItem".equals(operation)
            ? countBatchGetItems(requestItems)
            : countBatchWriteItems(requestItems);
    return batchSize == 0 ? null : batchSize;
  }

  private static long countBatchGetItems(Map<?, ?> requestItems) {
    long count = 0;
    for (Object keysAndAttributes : requestItems.values()) {
      List<?> keys = RequestAccess.getKeys(keysAndAttributes);
      if (keys != null) {
        count += keys.size();
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

  private static boolean isBatch(@Nullable Long batchSize) {
    return batchSize != null && batchSize > 1;
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
}
