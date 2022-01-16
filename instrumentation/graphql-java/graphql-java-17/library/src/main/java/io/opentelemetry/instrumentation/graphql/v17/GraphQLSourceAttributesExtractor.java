package io.opentelemetry.instrumentation.graphql.v17;

import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Document;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

public class GraphQLSourceAttributesExtractor
    implements AttributesExtractor<InstrumentationExecutionParameters, Document> {
  @Override
  public void onStart(AttributesBuilder attributes, InstrumentationExecutionParameters parameters) {
    String operationString = parameters.getQuery();
    attributes.put(GraphQLSingletons.GRAPHQL_SOURCE, operationString);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      InstrumentationExecutionParameters parameters,
      @Nullable Document document,
      @Nullable Throwable error) {}
}
