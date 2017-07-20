package com.datadoghq.trace.resolver;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.google.auto.service.AutoService;
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver {

  @Override
  protected Tracer resolve() {
    log.info("Creating the Datadog tracer");

    //Find a resource file named dd-trace.yml
    DDTracer tracer = null;
    //Create tracer from resource files
    tracer = DDTracerFactory.createFromConfigurationFile();



    return tracer;
  }

  public static Tracer registerTracer() {
    final Tracer tracer = TracerResolver.resolveTracer();

    if (tracer == null) {
      return NoopTracerFactory.create();
    }

    GlobalTracer.register(tracer);
    return tracer;
  }
}
