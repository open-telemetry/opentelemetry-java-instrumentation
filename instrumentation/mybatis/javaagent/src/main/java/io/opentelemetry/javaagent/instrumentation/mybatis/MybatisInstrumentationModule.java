package io.opentelemetry.javaagent.instrumentation.mybatis;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MybatisInstrumentationModule extends InstrumentationModule {

  public MybatisInstrumentationModule() {
    super("mybatis");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(new MybatisExecuteInstrumentation());
  }
}
