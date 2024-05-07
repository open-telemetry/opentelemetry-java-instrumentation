/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.internal;

import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE;

import graphql.ExecutionResult;
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
    attributes.put(GRAPHQL_OPERATION_NAME, request.getOperationName());
    if (request.getOperation() != null) {
      attributes.put(
          GRAPHQL_OPERATION_TYPE, request.getOperation().name().toLowerCase(Locale.ROOT));
    }
    attributes.put(GRAPHQL_DOCUMENT, request.getQuery());
  }
}
