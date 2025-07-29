/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v12_0;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationState;

final class OpenTelemetryInstrumentation extends SimpleInstrumentation {
  private final OpenTelemetryInstrumentationHelper helper;

  OpenTelemetryInstrumentation(OpenTelemetryInstrumentationHelper helper) {
    this.helper = helper;
  }

  @Override
  public InstrumentationState createState() {
    return new OpenTelemetryInstrumentationState();
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(
      InstrumentationExecutionParameters parameters) {
    OpenTelemetryInstrumentationState state = parameters.getInstrumentationState();
    return helper.beginExecution(state);
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters) {
    OpenTelemetryInstrumentationState state = parameters.getInstrumentationState();
    return helper.beginExecuteOperation(parameters, state);
  }

  @Override
  public DataFetcher<?> instrumentDataFetcher(
      DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
    OpenTelemetryInstrumentationState state = parameters.getInstrumentationState();
    return helper.instrumentDataFetcher(dataFetcher, state);
  }
}
