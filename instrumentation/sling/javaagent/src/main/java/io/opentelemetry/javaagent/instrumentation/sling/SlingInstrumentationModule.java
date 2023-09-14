package io.opentelemetry.javaagent.instrumentation.sling;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SlingInstrumentationModule extends InstrumentationModule {

  public SlingInstrumentationModule() {
    super("sling", "sling-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(new ServletResolverInstrumentation(), new SlingSafeMethodsServletInstrumentation());
  }

  @Override
  public int order() {
    return -1;
  }
}
