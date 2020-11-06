package io.opentelemetry.javaagent.instrumentation.javaclassloader;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClassLoaderInstrumentationModule extends InstrumentationModule {
  public ClassLoaderInstrumentationModule() {
    super("class-loader");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {"io.opentelemetry.javaagent.tooling.Constants"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClassLoaderInstrumentation(), new ResourceInjectionInstrumentation());
  }
}
