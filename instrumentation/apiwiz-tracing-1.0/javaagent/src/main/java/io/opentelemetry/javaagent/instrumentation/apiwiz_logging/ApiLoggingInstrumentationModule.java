package io.opentelemetry.javaagent.instrumentation.apiwiz_logging;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApiLoggingInstrumentationModule extends InstrumentationModule {

  public ApiLoggingInstrumentationModule() {
    super("apiwiz-tracing", "apiwiz-tracing-1.0");
    System.out.println("--------------Apiwiz ApiLoggingInstrumentationModule-----------------");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new ApiLoggingInstrumentation());
  }
}
