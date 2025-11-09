package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.util.Collections;
import java.util.List;

public class VertxRxJavaInstrumentation extends InstrumentationModule {

  public VertxRxJavaInstrumentation() {
    super("vertx", "vertx-rx-java");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new VertxRxJavaTypeInstrumentation());
  }
}
