package io.opentelemetry.instrumentation.graphql.v17;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.AstPrinter;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

public class GraphQLSourceParsedAttributesExtractor
    implements AttributesExtractor<InstrumentationExecuteOperationParameters, ExecutionResult> {
  @Override
  public void onStart(
      AttributesBuilder attributes, InstrumentationExecuteOperationParameters parameters) {
    String operationString =
        AstPrinter.printAst(parameters.getExecutionContext().getOperationDefinition());
    attributes.put(GraphQLSingletons.GRAPHQL_SOURCE, operationString);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      InstrumentationExecuteOperationParameters parameters,
      @Nullable ExecutionResult executionResult,
      @Nullable Throwable error) {}
}
