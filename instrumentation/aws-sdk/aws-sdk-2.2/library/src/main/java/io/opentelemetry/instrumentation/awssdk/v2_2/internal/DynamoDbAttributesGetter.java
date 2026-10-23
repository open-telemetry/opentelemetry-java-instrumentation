/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

class DynamoDbAttributesGetter implements DbClientAttributesGetter<ExecutionAttributes, Response> {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String AWS_DYNAMODB = "aws.dynamodb";

  private final MethodHandleFactory methodHandleFactory = new MethodHandleFactory();
  private final AwsSdkHttpAttributesGetter httpAttributesGetter = new AwsSdkHttpAttributesGetter();

  @Override
  @Nullable
  public String getDbQueryText(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(ExecutionAttributes executionAttributes) {
    String operation = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    Long batchSize = extractBatchSize(operation, executionAttributes);
    WriteOperationType writeOpType =
        "BatchWriteItem".equals(operation)
            ? extractWriteOperationType(executionAttributes)
            : WriteOperationType.NONE;
    return getStableOperationName(operation, batchSize, writeOpType);
  }

  @Override
  public String getDbSystemName(ExecutionAttributes executionAttributes) {
    return AWS_DYNAMODB;
  }

  @Override
  @Nullable
  public String getDbNamespace(ExecutionAttributes executionAttributes) {
    return null;
  }

  @Override
  @Nullable
  public String getDbCollectionName(ExecutionAttributes executionAttributes) {
    return extractTableName(executionAttributes);
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(ExecutionAttributes executionAttributes) {
    String operation = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    return extractBatchSize(operation, executionAttributes);
  }

  @Override
  @Nullable
  public String getDbResponseStatusCode(
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {
    return getServiceErrorCode(error);
  }

  @Override
  @Nullable
  public String getErrorType(
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {
    String serviceErrorCode = getServiceErrorCode(error);
    return serviceErrorCode != null
        ? serviceErrorCode
        : error == null ? null : error.getClass().getName();
  }

  @Nullable
  private static String getServiceErrorCode(@Nullable Throwable error) {
    if (!(error instanceof AwsServiceException)) {
      return null;
    }
    AwsErrorDetails details = ((AwsServiceException) error).awsErrorDetails();
    return details == null ? null : details.errorCode();
  }

  @Override
  @Nullable
  public String getServerAddress(ExecutionAttributes executionAttributes) {
    return httpAttributesGetter.getServerAddress(executionAttributes);
  }

  @Override
  @Nullable
  public Integer getServerPort(ExecutionAttributes executionAttributes) {
    return httpAttributesGetter.getServerPort(executionAttributes);
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

  private enum WriteOperationType {
    NONE,
    PUT,
    DELETE,
    MIXED
  }
}
