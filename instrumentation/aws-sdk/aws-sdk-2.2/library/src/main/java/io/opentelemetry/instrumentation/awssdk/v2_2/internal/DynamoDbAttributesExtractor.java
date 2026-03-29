/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
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
    if (operation != null) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(DB_OPERATION_NAME, operation);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_OPERATION, operation);
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {}
}
