package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.instrumentations.GrpcConnectionInstrumentation;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.instrumentations.RpcClientInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class NacosClientInstrumentationModule extends InstrumentationModule implements
    ExperimentalInstrumentationModule {
  public NacosClientInstrumentationModule() {
    super("nacos-client", "nacos-client-2.0.3");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new GrpcConnectionInstrumentation(),
        new RpcClientInstrumentation()
    );
  }
}
