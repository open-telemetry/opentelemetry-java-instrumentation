package io.opentelemetry.instrumentation.graphql.v17;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Locale;
import javax.annotation.Nullable;

final class GraphQLOperationNameAttributesExtractor
    implements AttributesExtractor<InstrumentationExecuteOperationParameters, ExecutionResult> {

  @Override
  public void onStart(
      AttributesBuilder attributes, InstrumentationExecuteOperationParameters parameters) {
    //    String operationName =
    // parameters.getExecutionContext().getOperationDefinition().getName();
    String operationName =
        parameters
            .getExecutionContext()
            .getOperationDefinition()
            .getOperation()
            .name()
            .toLowerCase(Locale.ROOT);
    attributes.put(GraphQLSingletons.OPERATION, operationName);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      InstrumentationExecuteOperationParameters parameters,
      @Nullable ExecutionResult executionResult,
      @Nullable Throwable error) {}
}
