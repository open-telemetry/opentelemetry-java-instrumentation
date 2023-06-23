package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient.v8_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

import static java.util.Collections.singletonList;

@AutoService(InstrumentationModule.class)
public class ElasticsearchApiClientInstrumentationModule extends InstrumentationModule {
  public ElasticsearchApiClientInstrumentationModule() {
    super("elasticsearch-client", "elasticsearch-api-client", "elasticsearch");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ApiClientInstrumentation());
  }
}
