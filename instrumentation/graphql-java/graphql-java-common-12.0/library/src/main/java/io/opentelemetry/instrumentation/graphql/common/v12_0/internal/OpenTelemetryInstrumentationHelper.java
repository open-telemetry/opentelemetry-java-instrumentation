/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlAttributesExtractor.GRAPHQL_DOCUMENT;
import static io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlAttributesExtractor.GRAPHQL_OPERATION_NAME;
import static io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlAttributesExtractor.GRAPHQL_OPERATION_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.AstPrinter;
import graphql.language.AstTransformer;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.Node;
import graphql.language.NodeVisitor;
import graphql.language.NodeVisitorStub;
import graphql.language.NullValue;
import graphql.language.OperationDefinition;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.DataFetcher;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OpenTelemetryInstrumentationHelper {
  private static final NodeVisitor sanitizingVisitor = new SanitizingVisitor();
  private static final AstTransformer astTransformer = new AstTransformer();

  private final Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> instrumenter;
  private final boolean captureQuery;
  private final boolean sanitizeQuery;
  private final boolean addOperationNameToSpanName;
  private final boolean operationSpanEnabled;
  private final boolean addAttributesToCurrentSpan;

  public static OpenTelemetryInstrumentationHelper create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean captureQuery,
      boolean sanitizeQuery,
      boolean addOperationNameToSpanName) {
    return create(
        openTelemetry,
        instrumentationName,
        captureQuery,
        sanitizeQuery,
        addOperationNameToSpanName,
        /* operationSpanEnabled= */ true,
        /* addAttributesToCurrentSpan= */ false);
  }

  public static OpenTelemetryInstrumentationHelper create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean captureQuery,
      boolean sanitizeQuery,
      boolean addOperationNameToSpanName,
      boolean operationSpanEnabled,
      boolean addAttributesToCurrentSpan) {
    InstrumenterBuilder<OpenTelemetryInstrumentationState, ExecutionResult> builder =
        Instrumenter.<OpenTelemetryInstrumentationState, ExecutionResult>builder(
                openTelemetry, instrumentationName, ignored -> "GraphQL Operation")
            .setSpanStatusExtractor(
                (spanStatusBuilder, instrumentationExecutionParameters, executionResult, error) -> {
                  if (executionResult != null && !executionResult.getErrors().isEmpty()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault()
                        .extract(
                            spanStatusBuilder,
                            instrumentationExecutionParameters,
                            executionResult,
                            error);
                  }
                });
    builder.addAttributesExtractor(new GraphqlAttributesExtractor());

    return new OpenTelemetryInstrumentationHelper(
        builder.buildInstrumenter(),
        captureQuery,
        sanitizeQuery,
        addOperationNameToSpanName,
        operationSpanEnabled,
        addAttributesToCurrentSpan);
  }

  private OpenTelemetryInstrumentationHelper(
      Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> instrumenter,
      boolean captureQuery,
      boolean sanitizeQuery,
      boolean addOperationNameToSpanName,
      boolean operationSpanEnabled,
      boolean addAttributesToCurrentSpan) {
    this.instrumenter = instrumenter;
    this.captureQuery = captureQuery;
    this.sanitizeQuery = sanitizeQuery;
    this.addOperationNameToSpanName = addOperationNameToSpanName;
    this.operationSpanEnabled = operationSpanEnabled;
    this.addAttributesToCurrentSpan = addAttributesToCurrentSpan;
  }

  public InstrumentationContext<ExecutionResult> beginExecution(
      OpenTelemetryInstrumentationState state) {

    Context parentContext = Context.current();

    // Capture the span that is current when execution begins (normally the enclosing server span)
    // so GraphQL telemetry can be stamped onto it. Null when there is no recording current span,
    // e.g. the standalone case or a non-recording remote parent.
    Span currentSpan = null;
    if (addAttributesToCurrentSpan) {
      Span span = Span.fromContext(parentContext);
      if (span.isRecording()) {
        currentSpan = span;
        state.setCurrentSpan(span);
      }
    }

    // Create our own operation span when it is enabled, or as a fallback when stamping onto the
    // current span was requested but there is no valid current span to stamp onto (so telemetry is
    // never silently lost).
    boolean wantOperationSpan =
        operationSpanEnabled || (addAttributesToCurrentSpan && currentSpan == null);
    boolean createOperationSpan =
        wantOperationSpan && instrumenter.shouldStart(parentContext, state);
    state.setOperationSpanCreated(createOperationSpan);

    if (createOperationSpan) {
      Context context = instrumenter.start(parentContext, state);
      state.setContext(context);
    } else {
      // Propagate the parent context so that data fetcher spans still nest correctly when the
      // operation span is not created.
      state.setContext(parentContext);
    }

    Span currentSpanForCompletion = currentSpan;
    return SimpleInstrumentationContext.whenCompleted(
        (result, throwable) -> {
          if (result != null) {
            List<GraphQLError> errors = result.getErrors();
            if (createOperationSpan) {
              addErrorEvents(Span.fromContext(state.getContext()), errors);
            }
            if (currentSpanForCompletion != null) {
              addErrorEvents(currentSpanForCompletion, errors);
              // The error status is promoted onto the current span whenever GraphQL attributes are
              // stamped onto it. This can mark an otherwise successful (e.g. HTTP 200) server span
              // as errored.
              if (!errors.isEmpty()) {
                currentSpanForCompletion.setStatus(StatusCode.ERROR);
              }
            }
          }

          if (createOperationSpan) {
            instrumenter.end(state.getContext(), state, result, throwable);
          }
        });
  }

  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters,
      OpenTelemetryInstrumentationState state) {
    OperationDefinition operationDefinition =
        parameters.getExecutionContext().getOperationDefinition();
    OperationDefinition.Operation operation = operationDefinition.getOperation();
    String operationType = operation.name().toLowerCase(Locale.ROOT);
    String operationName = operationDefinition.getName();

    state.setOperation(operation);
    state.setOperationName(operationName);

    String query = null;
    if (captureQuery) {
      Node<?> node = operationDefinition;
      if (sanitizeQuery) {
        node = sanitize(node);
      }
      query = AstPrinter.printAstCompact(node);
      state.setQuery(query);
    }

    // Only rename our own operation span; never rename the local root (e.g. the server span).
    // Guarded on whether we actually created the span, not merely on the config flag, so that a
    // suppressed span (state context == parent context) is never renamed.
    if (state.isOperationSpanCreated()) {
      String spanName = operationType;
      if (addOperationNameToSpanName && operationName != null && !operationName.isEmpty()) {
        spanName += " " + operationName;
      }
      Span.fromContext(state.getContext()).updateName(spanName);
    }

    Span currentSpan = state.getCurrentSpan();
    if (currentSpan != null) {
      stampOperationAttributes(currentSpan, operationName, operationType, query);
    }

    return SimpleInstrumentationContext.noOp();
  }

  private static void stampOperationAttributes(
      Span span,
      @Nullable String operationName,
      @Nullable String operationType,
      @Nullable String query) {
    // Null values are dropped by the OpenTelemetry API
    span.setAttribute(GRAPHQL_OPERATION_NAME, operationName);
    span.setAttribute(GRAPHQL_OPERATION_TYPE, operationType);
    span.setAttribute(GRAPHQL_DOCUMENT, query);
  }

  /**
   * Adds an {@code exception} event for each {@link GraphQLError} to the given span. Shared by the
   * operation-span, local-root, and data fetcher error paths.
   */
  public static void addErrorEvents(Span span, List<GraphQLError> errors) {
    for (GraphQLError error : errors) {
      AttributesBuilder attributes = Attributes.builder();
      attributes.put(EXCEPTION_TYPE, String.valueOf(error.getErrorType()));
      attributes.put(EXCEPTION_MESSAGE, error.getMessage());

      span.addEvent("exception", attributes.build());
    }
  }

  public DataFetcher<?> instrumentDataFetcher(
      DataFetcher<?> dataFetcher, OpenTelemetryInstrumentationState state) {
    Context context = state.getContext();

    return (DataFetcher<Object>)
        environment -> {
          try (Scope ignore = context.makeCurrent()) {
            return dataFetcher.get(environment);
          }
        };
  }

  private static Node<?> sanitize(Node<?> node) {
    return astTransformer.transform(node, sanitizingVisitor);
  }

  @SuppressWarnings("rawtypes") // super class uses Node without type parameter
  private static class SanitizingVisitor extends NodeVisitorStub {

    @Override
    protected TraversalControl visitValue(Value<?> node, TraverserContext<Node> context) {
      // replace values with ?
      EnumValue newValue = new EnumValue("?");
      return TreeTransformerUtil.changeNode(context, newValue);
    }

    private TraversalControl visitSafeValue(Value<?> node, TraverserContext<Node> context) {
      return super.visitValue(node, context);
    }

    @Override
    public TraversalControl visitVariableReference(
        VariableReference node, TraverserContext<Node> context) {
      return visitSafeValue(node, context);
    }

    @Override
    public TraversalControl visitBooleanValue(BooleanValue node, TraverserContext<Node> context) {
      return visitSafeValue(node, context);
    }

    @Override
    public TraversalControl visitNullValue(NullValue node, TraverserContext<Node> context) {
      return visitSafeValue(node, context);
    }
  }
}
