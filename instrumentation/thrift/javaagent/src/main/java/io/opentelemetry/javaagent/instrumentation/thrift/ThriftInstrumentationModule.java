package io.opentelemetry.javaagent.instrumentation.thrift;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

import static java.util.Arrays.asList;


@AutoService(InstrumentationModule.class)
public class ThriftInstrumentationModule extends InstrumentationModule {
    public ThriftInstrumentationModule(){
      super("thrift","thrift-0.14.1");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {

      return asList(
          new ThriftClientInstrumentation(),
          new ThriftTServerInstrumentation(),
          new ThriftTBaseProcessorInstrumentation(),
          new ThriftAsyncClientInstrumentation(),
          new ThriftTBaseAsyncProcessorInstrumentation(),
          new ThriftAsyncMethodCallInstrumentation()
          );
    }
}


