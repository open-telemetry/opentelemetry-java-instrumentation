/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlAttributesExtractor.GRAPHQL_DOCUMENT;
import static io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlAttributesExtractor.GRAPHQL_OPERATION_NAME;
import static io.opentelemetry.instrumentation.graphql.common.v12_0.internal.GraphqlAttributesExtractor.GRAPHQL_OPERATION_TYPE;

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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OpenTelemetryInstrumentationHelper {
  private static final String SCHEMA_URL = SchemaUrls.V1_45_0;

  private static final NodeVisitor sanitizingVisitor = new SanitizingVisitor();
  private static final AstTransformer astTransformer = new AstTransformer();

  private final Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> instrumenter;
  private final GraphqlErrorRecorder errorRecorder;
  private final boolean captureQuery;
  private final boolean sanitizeQuery;
  private final boolean addOperationNameToSpanName;
  private final boolean operationSpanEnabled;
  private final boolean spanEnrichmentEnabled;

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
        /* spanEnrichmentEnabled= */ false);
  }

  public static OpenTelemetryInstrumentationHelper create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean captureQuery,
      boolean sanitizeQuery,
      boolean addOperationNameToSpanName,
      boolean operationSpanEnabled,
      boolean spanEnrichmentEnabled) {
    // The GraphQL convention recommends SERVER spans, but execution can run beneath an HTTP
    // server or controller span, where a nested SERVER span could be suppressed. Keep the default
    // INTERNAL kind.
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
    builder.setSchemaUrl(SCHEMA_URL);
    return new OpenTelemetryInstrumentationHelper(
        builder.buildInstrumenter(),
        new GraphqlErrorRecorder(openTelemetry, instrumentationName, SCHEMA_URL),
        captureQuery,
        sanitizeQuery,
        addOperationNameToSpanName,
        operationSpanEnabled,
        spanEnrichmentEnabled);
  }

  private OpenTelemetryInstrumentationHelper(
      Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> instrumenter,
      GraphqlErrorRecorder errorRecorder,
      boolean captureQuery,
      boolean sanitizeQuery,
      boolean addOperationNameToSpanName,
      boolean operationSpanEnabled,
      boolean spanEnrichmentEnabled) {
    this.instrumenter = instrumenter;
    this.errorRecorder = errorRecorder;
    this.captureQuery = captureQuery;
    this.sanitizeQuery = sanitizeQuery;
    this.addOperationNameToSpanName = addOperationNameToSpanName;
    this.operationSpanEnabled = operationSpanEnabled;
    this.spanEnrichmentEnabled = spanEnrichmentEnabled;
  }

  public InstrumentationContext<ExecutionResult> beginExecution(
      OpenTelemetryInstrumentationState state) {

    Context parentContext = Context.current();

    // False when instrumentation is suppressed. That must apply to enriching the current span as
    // well as to creating the operation span.
    boolean shouldStart = instrumenter.shouldStart(parentContext, state);

    // Capture the span that is current when execution begins (normally the enclosing server span)
    // so GraphQL telemetry can be stamped onto it. Null when there is no recording current span,
    // e.g. the standalone case or a non-recording remote parent.
    Span currentSpan = null;
    if (spanEnrichmentEnabled && shouldStart) {
      Span span = Span.fromContext(parentContext);
      if (span.isRecording()) {
        currentSpan = span;
        state.setCurrentSpan(span);
      }
    }

    // Create our own operation span when it is enabled, or as a fallback when stamping onto the
    // current span was requested but there is no recording current span to stamp onto (so telemetry
    // is never silently lost).
    boolean wantOperationSpan =
        operationSpanEnabled || (spanEnrichmentEnabled && currentSpan == null);
    boolean createOperationSpan = wantOperationSpan && shouldStart;
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
    return SimpleInstrumentationContext.whenCompleted((result, throwable) -> {
      if (result != null) {
        List<GraphQLError> errors = result.getErrors();
        // Record each error once: on the operation span when we created it, otherwise on the
        // current span. parentContext carries the current span. Recording on both would emit
        // duplicate exception logs.
        if (createOperationSpan) {
          recordErrors(state.getContext(), errors);
        } else if (currentSpanForCompletion != null) {
          recordErrors(parentContext, errors);
        }
        // The error status is promoted onto the current span whenever GraphQL attributes are
        // stamped onto it. This can mark an otherwise successful (e.g. HTTP 200) server span
        // as errored.
        if (currentSpanForCompletion != null && !errors.isEmpty()) {
          currentSpanForCompletion.setStatus(StatusCode.ERROR);
        }
      }

      if (createOperationSpan) {
        instrumenter.end(state.getContext(), state, result, throwable);
      }
    });
  }

  private void recordErrors(Context context, List<GraphQLError> errors) {
    for (GraphQLError error : errors) {
      errorRecorder.record(context, error);
    }
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

    // Only rename our own operation span; never rename the current span (e.g. the server span).
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
