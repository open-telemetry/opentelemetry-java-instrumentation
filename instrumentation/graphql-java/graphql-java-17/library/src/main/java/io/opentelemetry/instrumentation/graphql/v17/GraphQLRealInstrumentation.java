package io.opentelemetry.instrumentation.graphql.v17;

import graphql.ExecutionResult;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("FieldCanBeLocal")
public class GraphQLRealInstrumentation extends SimpleInstrumentation {

  private GraphQLInstrumentationState state;
  private final Map<ExecutionId, ContextAndFieldFetchParameters> resolveMap;

  private final Instrumenter<InstrumentationExecutionParameters, Document> parseInstrumenter;
  private final Instrumenter<InstrumentationExecutionParameters, List<ValidationError>>
      validateInstrumenter;
  private final Instrumenter<InstrumentationExecutionParameters, ExecutionResult>
      executionInstrumenter;
  private final Instrumenter<InstrumentationExecuteOperationParameters, ExecutionResult>
      executeOperationInstrumenter;
  private final Instrumenter<InstrumentationFieldFetchParameters, Object> fieldFetchInstrumenter;

  public GraphQLRealInstrumentation(OpenTelemetry openTelemetry) {
    if (openTelemetry == null) {
      throw new NullPointerException("Parameter openTelemetry may not be null");
    }
    this.resolveMap = new ConcurrentHashMap<>();

    this.executionInstrumenter =
        Instrumenter.<InstrumentationExecutionParameters, ExecutionResult>builder(
                openTelemetry,
                GraphQLSingletons.INSTRUMENTATION_NAME,
                ignored -> GraphQLSingletons.SPAN_EXECUTE)
            .newInstrumenter();
    this.parseInstrumenter =
        Instrumenter.<InstrumentationExecutionParameters, Document>builder(
                openTelemetry,
                GraphQLSingletons.INSTRUMENTATION_NAME,
                ignored -> GraphQLSingletons.SPAN_PARSE)
            .addAttributesExtractor(new GraphQLSourceAttributesExtractor())
            .newInstrumenter();
    this.validateInstrumenter =
        Instrumenter.<InstrumentationExecutionParameters, List<ValidationError>>builder(
                openTelemetry,
                GraphQLSingletons.INSTRUMENTATION_NAME,
                ignored -> GraphQLSingletons.SPAN_VALIDATE)
            .newInstrumenter();
    this.executeOperationInstrumenter =
        Instrumenter.<InstrumentationExecuteOperationParameters, ExecutionResult>builder(
                openTelemetry,
                GraphQLSingletons.INSTRUMENTATION_NAME,
                ignored -> GraphQLSingletons.SPAN_EXECUTE)
            .addAttributesExtractor(new GraphQLOperationNameAttributesExtractor())
            .addAttributesExtractor(new GraphQLSourceParsedAttributesExtractor())
            .newInstrumenter();
    this.fieldFetchInstrumenter =
        Instrumenter.<InstrumentationFieldFetchParameters, Object>builder(
                openTelemetry,
                GraphQLSingletons.INSTRUMENTATION_NAME,
                ignored -> GraphQLSingletons.SPAN_RESOLVE)
            .addAttributesExtractor(new GraphQLFieldFetchAttributesExtractor())
            .newInstrumenter();
  }

  @SuppressWarnings("MethodCanBeStatic")
  private Context currentContext() {
    //    if (state != null) {
    //      System.err.println("State != null: " + state);
    //      Context context = state.getContext();
    //      if (context != null) {
    //        System.err.println("Context != null: " + context);
    //        return context;
    //      } else {
    //        System.err.println("Context == null");
    //      }
    //    } else {
    //      System.err.println("State == null");
    //    }
    return Context.current();
  }

  @Override
  public InstrumentationState createState() {
    System.err.println("Call to createState");
    return state = new GraphQLInstrumentationState(currentContext()); // TODO
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      InstrumentationExecuteOperationParameters parameters) {

    System.err.println(Thread.currentThread().getName());
    Context parentContext = currentContext();
    if (!executeOperationInstrumenter.shouldStart(parentContext, parameters)) {
      return new SimpleInstrumentationContext<>();
    }

    Context context = executeOperationInstrumenter.start(parentContext, parameters);
    Scope scope = context.makeCurrent();

    return new SimpleInstrumentationContext<ExecutionResult>() {
      @Override
      public void onCompleted(ExecutionResult result, Throwable t) {
        executeOperationInstrumenter.end(context, parameters, result, t);
        scope.close();
      }
    };
  }

  @Override
  public InstrumentationContext<Object> beginFieldFetch(
      InstrumentationFieldFetchParameters parameters) {
    System.err.println(Thread.currentThread().getName());
    Context parentContext = currentContext();

    System.err.println(parameters.getEnvironment().getExecutionId());
    if (!fieldFetchInstrumenter.shouldStart(parentContext, parameters)) {
      return new SimpleInstrumentationContext<>();
    }

    Context context = fieldFetchInstrumenter.start(parentContext, parameters);
    //    Scope scope = context.makeCurrent();

    return new SimpleInstrumentationContext<Object>() {
      @Override
      public void onCompleted(Object result, Throwable t) {
        fieldFetchInstrumenter.end(context, parameters, result, t);
        //        scope.close();
      }
    };
  }

  //  @Override
  //  public InstrumentationContext<ExecutionResult> beginExecution(
  //      InstrumentationExecutionParameters parameters) {
  //
  //    System.err.println(Thread.currentThread().getName());
  //    Context parentContext = currentContext();
  //    if (!executionInstrumenter.shouldStart(parentContext, parameters)) {
  //      return new SimpleInstrumentationContext<>();
  //    }
  //
  //    Context context = executionInstrumenter.start(parentContext, parameters);
  //    Scope scope = context.makeCurrent();
  //
  //    return new SimpleInstrumentationContext<ExecutionResult>() {
  //      @Override
  //      public void onCompleted(ExecutionResult result, Throwable t) {
  //        executionInstrumenter.end(context, parameters, result, t);
  //        scope.close();
  //      }
  //    };
  //  }

  @Override
  public InstrumentationContext<Document> beginParse(
      InstrumentationExecutionParameters parameters) {

    System.err.println(Thread.currentThread().getName());
    Context parentContext = currentContext();
    if (!parseInstrumenter.shouldStart(parentContext, parameters)) {
      return new SimpleInstrumentationContext<>();
    }

    Context context = parseInstrumenter.start(parentContext, parameters);
    Scope scope = context.makeCurrent();

    return new SimpleInstrumentationContext<Document>() {
      @Override
      public void onCompleted(Document result, Throwable t) {
        parseInstrumenter.end(context, parameters, result, t);
        scope.close();
      }
    };
  }

  @Override
  public InstrumentationContext<List<ValidationError>> beginValidation(
      InstrumentationValidationParameters parameters) {

    System.err.println(Thread.currentThread().getName());
    Context parentContext = currentContext();
    if (!validateInstrumenter.shouldStart(parentContext, parameters)) {
      return new SimpleInstrumentationContext<>();
    }

    Context context = validateInstrumenter.start(parentContext, parameters);
    Scope scope = context.makeCurrent();

    return new SimpleInstrumentationContext<List<ValidationError>>() {
      @Override
      public void onCompleted(List<ValidationError> result, Throwable t) {
        validateInstrumenter.end(context, parameters, result, t);
        scope.close();
      }
    };
  }
}
