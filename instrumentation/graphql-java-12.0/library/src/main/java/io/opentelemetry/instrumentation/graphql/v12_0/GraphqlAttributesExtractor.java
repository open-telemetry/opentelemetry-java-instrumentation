/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v12_0;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Locale;
import javax.annotation.Nullable;

final class GraphqlAttributesExtractor
    implements AttributesExtractor<InstrumentationExecutionParameters, ExecutionResult> {
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
      InstrumentationExecutionParameters request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      InstrumentationExecutionParameters request,
      @Nullable ExecutionResult response,
      @Nullable Throwable error) {
    OpenTelemetryInstrumentationState state = request.getInstrumentationState();
    attributes.put(OPERATION_NAME, state.getOperationName());
    if (state.getOperation() != null) {
      attributes.put(OPERATION_TYPE, state.getOperation().name().toLowerCase(Locale.ROOT));
    }
    attributes.put(GRAPHQL_DOCUMENT, state.getQuery());
  }
}
