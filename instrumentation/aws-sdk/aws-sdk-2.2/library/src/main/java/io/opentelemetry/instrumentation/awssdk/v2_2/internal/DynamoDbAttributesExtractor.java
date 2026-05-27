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
import java.util.Set;
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
      // The instanceof check above guarantees Map, only the generic parameters are unchecked.
      @SuppressWarnings("unchecked")
      Set<String> tables = ((Map<String, ?>) requestItems.get()).keySet();
      if (tables.size() == 1) {
        return tables.iterator().next();
      }
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
