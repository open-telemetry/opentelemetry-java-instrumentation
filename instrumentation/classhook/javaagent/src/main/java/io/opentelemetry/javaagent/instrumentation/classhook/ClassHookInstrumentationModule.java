package io.opentelemetry.javaagent.instrumentation.classhook;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;


@AutoService(InstrumentationModule.class)
public class ClassHookInstrumentationModule extends InstrumentationModule {
  public ClassHookInstrumentationModule() {
    super("class-hook", "class-hook-0.1");
  }

  @Override
  public int order() {
    return 100;
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("com.azure.spring.cloud.test.config.client");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new ClassHookInstrumentation());
  }
}

