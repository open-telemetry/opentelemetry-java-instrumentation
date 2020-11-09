package io.opentelemetry.javaagent.instrumentation.struts2;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Struts2InstrumentationModule extends InstrumentationModule {

  public Struts2InstrumentationModule() {
    super("struts-2");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Struts2Tracer"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ActionInvocationInstrumentation());
  }
}
