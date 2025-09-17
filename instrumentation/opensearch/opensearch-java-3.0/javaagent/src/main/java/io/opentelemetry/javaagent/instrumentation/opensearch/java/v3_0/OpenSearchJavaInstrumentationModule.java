package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class OpenSearchJavaInstrumentationModule extends InstrumentationModule {
  public OpenSearchJavaInstrumentationModule() {
    super("opensearch-java", "opensearch-java-3.0", "opensearch");
  }

    @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new OpenSearchTransportInstrumentation());
  }
}
