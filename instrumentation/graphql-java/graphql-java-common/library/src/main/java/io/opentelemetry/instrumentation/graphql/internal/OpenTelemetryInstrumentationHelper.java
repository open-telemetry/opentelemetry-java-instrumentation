/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.internal;

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
import io.opentelemetry.semconv.ExceptionAttributes;
import java.util.Locale;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetryInstrumentationHelper {
  private static final NodeVisitor sanitizingVisitor = new SanitizingVisitor();
  private static final AstTransformer astTransformer = new AstTransformer();

  private final Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> instrumenter;
  private final boolean sanitizeQuery;

  private OpenTelemetryInstrumentationHelper(
      Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> instrumenter,
      boolean sanitizeQuery) {
    this.instrumenter = instrumenter;
    this.sanitizeQuery = sanitizeQuery;
  }

  public static OpenTelemetryInstrumentationHelper create(
      OpenTelemetry openTelemetry, String instrumentationName, boolean sanitizeQuery) {
    InstrumenterBuilder<OpenTelemetryInstrumentationState, ExecutionResult> builder =
        Instrumenter.<OpenTelemetryInstrumentationState, ExecutionResult>builder(
                openTelemetry, instrumentationName, ignored -> "GraphQL Operation")
            .setSpanStatusExtractor(
                (spanStatusBuilder, instrumentationExecutionParameters, executionResult, error) -> {
                  if (!executionResult.getErrors().isEmpty()) {
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

    return new OpenTelemetryInstrumentationHelper(builder.buildInstrumenter(), sanitizeQuery);
  }

  public InstrumentationContext<ExecutionResult> beginExecution(
      OpenTelemetryInstrumentationState state) {

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, state)) {
      return SimpleInstrumentationContext.noOp();
    }

    Context context = instrumenter.start(parentContext, state);
    state.setContext(context);

    return SimpleInstrumentationContext.whenCompleted(
        (result, throwable) -> {
          Span span = Span.fromContext(context);
          for (GraphQLError error : result.getErrors()) {
            AttributesBuilder attributes = Attributes.builder();
            attributes.put(
                ExceptionAttributes.EXCEPTION_TYPE, String.valueOf(error.getErrorType()));
            attributes.put(ExceptionAttributes.EXCEPTION_MESSAGE, error.getMessage());

            span.addEvent("exception", attributes.build());
          }

          instrumenter.end(context, state, result, throwable);
        });
  }

  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters,
      OpenTelemetryInstrumentationState state) {
    Span span = Span.fromContext(state.getContext());

    OperationDefinition operationDefinition =
        parameters.getExecutionContext().getOperationDefinition();
    OperationDefinition.Operation operation = operationDefinition.getOperation();
    String operationType = operation.name().toLowerCase(Locale.ROOT);
    String operationName = operationDefinition.getName();

    String spanName = operationType;
    if (operationName != null && !operationName.isEmpty()) {
      spanName += " " + operationName;
    }
    span.updateName(spanName);

    state.setOperation(operation);
    state.setOperationName(operationName);

    Node<?> node = operationDefinition;
    if (sanitizeQuery) {
      node = sanitize(node);
    }
    state.setQuery(AstPrinter.printAst(node));

    return SimpleInstrumentationContext.noOp();
  }

  public DataFetcher<?> instrumentDataFetcher(
      DataFetcher<?> dataFetcher, OpenTelemetryInstrumentationState state) {
    Context context = state.getContext();

    return (DataFetcher<Object>)
        environment -> {
          try (Scope scope = context.makeCurrent()) {
            return dataFetcher.get(environment);
          }
        };
  }

  private static Node<?> sanitize(Node<?> node) {
    return astTransformer.transform(node, sanitizingVisitor);
  }

  @SuppressWarnings("rawtypes")
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
