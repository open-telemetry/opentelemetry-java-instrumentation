package io.opentelemetry.javaagent.instrumentation.graphql.v17;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class GraphQLInstrumentationModule extends InstrumentationModule {

  protected GraphQLInstrumentationModule() {
    super("graphql-java", "graphql-java-17");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new GraphQLChainInjectInstrumentation());
  }
}
