/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
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
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_OPERATION_NAME, getStableOperationName(operation));
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
  private static String getStableOperationName(@Nullable String operation) {
    if ("BatchGetItem".equals(operation)) {
      return "BATCH GetItem";
    }
    if ("BatchWriteItem".equals(operation)) {
      return "BATCH WriteItem";
    }
    return operation;
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
