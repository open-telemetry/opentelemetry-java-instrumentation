/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
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
    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_OPERATION_NAME, operation);
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
  @SuppressWarnings("rawtypes")
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
    // Batch operations (BatchGetItem, BatchWriteItem) use a map keyed by table names.
    // When there is exactly one table, return it; otherwise return the sentinel for multiple tables.
    Optional<Map> requestItems = request.getValueForField("RequestItems", Map.class);
    if (requestItems.isPresent() && !requestItems.get().isEmpty()) {
      Map<?, ?> items = requestItems.get();
      if (items.size() == 1) {
        return items.keySet().iterator().next().toString();
      }
      return "MULTIPLE_TABLES";
    }
    return null;
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {}
}
