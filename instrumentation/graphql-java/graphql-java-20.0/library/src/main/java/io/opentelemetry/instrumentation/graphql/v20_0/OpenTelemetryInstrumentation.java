/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static graphql.execution.instrumentation.InstrumentationState.ofState;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationHelper;
import io.opentelemetry.instrumentation.graphql.internal.OpenTelemetryInstrumentationState;
import io.opentelemetry.semconv.ExceptionAttributes;
import java.util.concurrent.CompletionStage;

final class OpenTelemetryInstrumentation extends SimplePerformantInstrumentation {
  private final OpenTelemetryInstrumentationHelper helper;
  private final Instrumenter<DataFetchingEnvironment, Object> dataFetcherInstrumenter;
  private final boolean createSpansForTrivialDataFetcher;

  OpenTelemetryInstrumentation(
      OpenTelemetryInstrumentationHelper helper,
      Instrumenter<DataFetchingEnvironment, Object> dataFetcherInstrumenter,
      boolean createSpansForTrivialDataFetcher) {
    this.helper = helper;
    this.dataFetcherInstrumenter = dataFetcherInstrumenter;
    this.createSpansForTrivialDataFetcher = createSpansForTrivialDataFetcher;
  }

  @Override
  public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
    return new Graphql20OpenTelemetryInstrumentationState();
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

    Graphql20OpenTelemetryInstrumentationState state = ofState(rawState);

    return environment -> {
      ResultPath path = environment.getExecutionStepInfo().getPath();
      Context parentContext = state.getParentContextForPath(path);

      if (!dataFetcherInstrumenter.shouldStart(parentContext, environment)
          || (parameters.isTrivialDataFetcher() && !createSpansForTrivialDataFetcher)) {
        // Propagate context only, do not create span
        try (Scope ignored = parentContext.makeCurrent()) {
          return dataFetcher.get(environment);
        }
      }

      // Start span
      Context childContext = dataFetcherInstrumenter.start(parentContext, environment);
      state.setContextForPath(path, childContext);

      boolean isCompletionStage = false;

      Object fieldValue = null;
      try (Scope ignored = childContext.makeCurrent()) {
        fieldValue = dataFetcher.get(environment);
        isCompletionStage = fieldValue instanceof CompletionStage;

        if (isCompletionStage) {
          return ((CompletionStage<?>) fieldValue)
              .whenComplete(
                  (result, throwable) -> {
                    handleDataFetcherResult(childContext, result);
                    dataFetcherInstrumenter.end(childContext, environment, result, throwable);
                  });
        }
        return fieldValue;
      } catch (Throwable throwable) {
        dataFetcherInstrumenter.end(childContext, environment, null, throwable);
        throw throwable;
      } finally {
        if (!isCompletionStage) {
          handleDataFetcherResult(childContext, fieldValue);
          dataFetcherInstrumenter.end(childContext, environment, fieldValue, null);
        }
      }
    };
  }

  private static void handleDataFetcherResult(Context context, Object result) {
    if (!(result instanceof DataFetcherResult)) {
      return;
    }

    DataFetcherResult<?> dataFetcherResult = (DataFetcherResult<?>) result;
    Span span = Span.fromContext(context);
    for (GraphQLError error : dataFetcherResult.getErrors()) {
      AttributesBuilder attributes = Attributes.builder();
      attributes.put(ExceptionAttributes.EXCEPTION_TYPE, String.valueOf(error.getErrorType()));
      attributes.put(ExceptionAttributes.EXCEPTION_MESSAGE, error.getMessage());

      span.addEvent("exception", attributes.build());
    }
  }
}
