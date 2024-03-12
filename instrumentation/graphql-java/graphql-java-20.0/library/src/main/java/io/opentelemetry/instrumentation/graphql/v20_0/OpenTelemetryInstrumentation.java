/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static graphql.execution.instrumentation.InstrumentationState.ofState;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationState;

final class OpenTelemetryInstrumentation extends SimplePerformantInstrumentation {
  private final OpenTelemetryInstrumentationHelper helper;

  OpenTelemetryInstrumentation(OpenTelemetryInstrumentationHelper helper) {
    this.helper = helper;
  }

  @Override
  public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
    return new OpenTelemetryInstrumentationState();
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(
      InstrumentationExecutionParameters parameters, InstrumentationState rawState) {
    OpenTelemetryInstrumentationState state = ofState(rawState);
    return helper.beginExecution(state);
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters, InstrumentationState rawState) {
    OpenTelemetryInstrumentationState state = ofState(rawState);
    return helper.beginExecuteOperation(parameters, state);
  }

  @Override
  public DataFetcher<?> instrumentDataFetcher(
      DataFetcher<?> dataFetcher,
      InstrumentationFieldFetchParameters parameters,
      InstrumentationState rawState) {
    OpenTelemetryInstrumentationState state = ofState(rawState);
    return helper.instrumentDataFetcher(dataFetcher, state);
  }
}
