package io.opentelemetry.instrumentation.graphql.v17;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.AstPrinter;
import graphql.schema.idl.SchemaPrinter;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

public class GraphQLFieldFetchAttributesExtractor
    implements AttributesExtractor<InstrumentationFieldFetchParameters, Object> {
  @Override
  public void onStart(
      AttributesBuilder attributes, InstrumentationFieldFetchParameters parameters) {
    SchemaPrinter printer = new SchemaPrinter();
    attributes.put(GraphQLSingletons.FIELD_NAME, parameters.getField().getName());
    //    attributes.put(
    //        GraphQLSingletons.FIELD_PATH,
    //        parameters.getEnvironment().getFieldDefinition().getName()); // TODO
    attributes.put(
        GraphQLSingletons.FIELD_TYPE,
        AstPrinter.printAstCompact(parameters.getField().getDefinition().getType()));
    //    attributes.put(
    //        GraphQLSingletons.GRAPHQL_SOURCE,
    //        AstPrinter.printAst(parameters.getExecutionContext().getExecutionInput().));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      InstrumentationFieldFetchParameters parameters,
      @Nullable Object o,
      @Nullable Throwable error) {}
}
