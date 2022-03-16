/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.AstPrinter;
import graphql.language.AstTransformer;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.NullValue;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.DataFetcher;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public final class OpenTelemetryInstrumentation extends SimpleInstrumentation {
  private final Instrumenter<InstrumentationExecutionParameters, ExecutionResult> instrumenter;
  private final boolean captureExperimentalSpanAttributes;
  private final boolean sanitizeQuery;

  OpenTelemetryInstrumentation(
      Instrumenter<InstrumentationExecutionParameters, ExecutionResult> instrumenter,
      boolean captureExperimentalSpanAttributes,
      boolean sanitizeQuery) {
    this.instrumenter = instrumenter;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.sanitizeQuery = sanitizeQuery;
  }

  @Override
  public InstrumentationState createState() {
    return new OpenTelemetryInstrumentationState();
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(
      InstrumentationExecutionParameters parameters) {

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, parameters)) {
      return SimpleInstrumentationContext.noOp();
    }

    Context context = instrumenter.start(parentContext, parameters);
    OpenTelemetryInstrumentationState state = parameters.getInstrumentationState();
    state.setContext(context);

    return SimpleInstrumentationContext.whenCompleted(
        (result, throwable) -> {
          Span span = Span.fromContext(context);
          for (GraphQLError error : result.getErrors()) {
            AttributesBuilder attributes = Attributes.builder();
            attributes.put(SemanticAttributes.EXCEPTION_TYPE, String.valueOf(error.getErrorType()));
            attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, error.getMessage());

            span.addEvent(SemanticAttributes.EXCEPTION_EVENT_NAME, attributes.build());
          }

          instrumenter.end(context, parameters, result, throwable);
        });
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters) {

    OpenTelemetryInstrumentationState state = parameters.getInstrumentationState();
    Span span = Span.fromContext(state.getContext());

    OperationDefinition operationDefinition =
        parameters.getExecutionContext().getOperationDefinition();
    Operation operation = operationDefinition.getOperation();
    String operationName = operationDefinition.getName();

    String spanName = operation.name();
    if (operationName != null && !operationName.isEmpty()) {
      spanName += " " + operationName;
    }
    span.updateName(spanName);

    if (captureExperimentalSpanAttributes) {
      state.setOperation(operation);
      state.setOperationName(operationName);

      Node<?> node = operationDefinition;
      if (sanitizeQuery) {
        node = sanitize(node);
      }
      state.setQuery(AstPrinter.printAst(node));
    }

    return SimpleInstrumentationContext.noOp();
  }

  private static Node<?> sanitize(Node<?> node) {
    @SuppressWarnings("rawtypes")
    NodeVisitorStub visitor =
        new NodeVisitorStub() {
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
          public TraversalControl visitBooleanValue(
              BooleanValue node, TraverserContext<Node> context) {
            return visitSafeValue(node, context);
          }

          @Override
          public TraversalControl visitNullValue(NullValue node, TraverserContext<Node> context) {
            return visitSafeValue(node, context);
          }
        };
    AstTransformer astTransformer = new AstTransformer();
    return astTransformer.transform(node, visitor);
  }

  @Override
  public DataFetcher<?> instrumentDataFetcher(
      DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
    OpenTelemetryInstrumentationState state = parameters.getInstrumentationState();
    Context context = state.getContext();

    return (DataFetcher<Object>)
        environment -> {
          try (Scope scope = context.makeCurrent()) {
            return dataFetcher.get(environment);
          }
        };
  }
}
