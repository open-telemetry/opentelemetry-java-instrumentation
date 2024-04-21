/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import static graphql.execution.instrumentation.InstrumentationState.ofState;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
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
import graphql.schema.DataFetchingEnvironment;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.ExceptionAttributes;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

final class OpenTelemetryInstrumentation extends SimplePerformantInstrumentation {

  private static final NodeVisitor sanitizingVisitor = new SanitizingVisitor();
  private static final AstTransformer astTransformer = new AstTransformer();

  private final Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult>
      executionInstrumenter;

  private final boolean sanitizeQuery;

  private final Instrumenter<DataFetchingEnvironment, Void> dataFetcherInstrumenter;

  private final boolean createSpansForTrivialDataFetcher;

  OpenTelemetryInstrumentation(
      Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult> executionInstrumenter,
      boolean sanitizeQuery,
      Instrumenter<DataFetchingEnvironment, Void> dataFetcherInstrumenter,
      boolean createSpansForTrivialDataFetcher) {
    this.executionInstrumenter = executionInstrumenter;
    this.sanitizeQuery = sanitizeQuery;
    this.dataFetcherInstrumenter = dataFetcherInstrumenter;
    this.createSpansForTrivialDataFetcher = createSpansForTrivialDataFetcher;
  }

  @Override
  public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
    return new OpenTelemetryInstrumentationState();
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(
      InstrumentationExecutionParameters parameters, InstrumentationState rawState) {

    OpenTelemetryInstrumentationState state = ofState(rawState);
    Context parentContext = Context.current();

    if (!executionInstrumenter.shouldStart(parentContext, state)) {
      return SimpleInstrumentationContext.noOp();
    }

    Context context = executionInstrumenter.start(parentContext, state);
    state.setContext(context);

    return SimpleInstrumentationContext.whenCompleted(
        (result, throwable) -> {
          Span span = Span.fromContext(context);

          // TODO: Needs revisions. Exceptions should be recording by using 'span.recordException'
          for (GraphQLError error : result.getErrors()) {
            AttributesBuilder attributes = Attributes.builder();
            attributes.put(
                ExceptionAttributes.EXCEPTION_TYPE, String.valueOf(error.getErrorType()));
            attributes.put(ExceptionAttributes.EXCEPTION_MESSAGE, error.getMessage());

            span.addEvent("exception", attributes.build());
          }

          executionInstrumenter.end(context, state, result, throwable);
        });
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters, InstrumentationState rawState) {

    OpenTelemetryInstrumentationState state = ofState(rawState);
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

  @Override
  public DataFetcher<?> instrumentDataFetcher(
      DataFetcher<?> dataFetcher,
      InstrumentationFieldFetchParameters parameters,
      InstrumentationState rawState) {

    OpenTelemetryInstrumentationState state = ofState(rawState);

    return environment -> {
      ResultPath path = environment.getExecutionStepInfo().getPath();
      Context parentContext = state.getParentContextForPath(path);

      if (!dataFetcherInstrumenter.shouldStart(parentContext, environment)) {
        // Propagate context only, do not create span
        try (Scope ignored = parentContext.makeCurrent()) {
          return dataFetcher.get(environment);
        }
      }

      if (parameters.isTrivialDataFetcher() && !createSpansForTrivialDataFetcher) {
        // Propagate context only, do not create span
        try (Scope ignored = parentContext.makeCurrent()) {
          return dataFetcher.get(environment);
        }
      }

      // Start span
      Context childContext = dataFetcherInstrumenter.start(parentContext, environment);
      state.setContextForPath(path, childContext);

      boolean isCompletionStage = false;

      try {
        Object fieldValue = dataFetcher.get(environment);

        isCompletionStage = fieldValue instanceof CompletionStage;

        if (isCompletionStage) {
          return ((CompletionStage<?>) fieldValue)
              .whenComplete(
                  (result, throwable) ->
                      dataFetcherInstrumenter.end(childContext, environment, null, throwable));
        }

        return fieldValue;

      } catch (Throwable throwable) {
        dataFetcherInstrumenter.end(childContext, environment, null, throwable);
        throw throwable;

      } finally {
        if (!isCompletionStage) {
          dataFetcherInstrumenter.end(childContext, environment, null, null);
        }
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
