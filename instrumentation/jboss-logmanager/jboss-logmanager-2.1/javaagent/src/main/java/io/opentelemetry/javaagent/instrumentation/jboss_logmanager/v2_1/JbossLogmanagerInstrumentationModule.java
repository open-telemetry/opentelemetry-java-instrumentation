package io.opentelemetry.javaagent.instrumentation.jboss_logmanager.v2_1;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import static java.util.Collections.singletonList;

@AutoService(InstrumentationModule.class)
public class JbossLogmanagerInstrumentationModule extends InstrumentationModule {

  public JbossLogmanagerInstrumentationModule() {
    super("jboss-logmanager", "jboss-logmanager-2.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JbossLogmanagerInstrumentation());
  }
}
