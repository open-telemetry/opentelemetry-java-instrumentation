/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.internal;

import graphql.ExecutionResult;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class GraphqlAttributesExtractor
    implements AttributesExtractor<OpenTelemetryInstrumentationState, ExecutionResult> {
  // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/graphql.md
  private static final AttributeKey<String> OPERATION_NAME =
      AttributeKey.stringKey("graphql.operation.name");
  private static final AttributeKey<String> OPERATION_TYPE =
      AttributeKey.stringKey("graphql.operation.type");
  private static final AttributeKey<String> GRAPHQL_DOCUMENT =
      AttributeKey.stringKey("graphql.document");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      OpenTelemetryInstrumentationState request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      OpenTelemetryInstrumentationState request,
      @Nullable ExecutionResult response,
      @Nullable Throwable error) {
    attributes.put(OPERATION_NAME, request.getOperationName());
    if (request.getOperation() != null) {
      attributes.put(OPERATION_TYPE, request.getOperation().name().toLowerCase(Locale.ROOT));
    }
    attributes.put(GRAPHQL_DOCUMENT, request.getQuery());
  }
}
