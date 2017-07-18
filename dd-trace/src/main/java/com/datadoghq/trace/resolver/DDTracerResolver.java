package com.datadoghq.trace.resolver;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.integration.DDSpanContextDecorator;
import com.google.auto.service.AutoService;
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver {

  private static final Logger logger = LoggerFactory.getLogger(DDTracerResolver.class);

  @Override
  protected Tracer resolve() {
    logger.info("Creating the Datadog tracer");

    //Find a resource file named dd-trace.yml
    DDTracer tracer = null;
    //Create tracer from resource files
    tracer = DDTracerFactory.createFromConfigurationFile();

    //Create decorators from resource files
    final List<DDSpanContextDecorator> decorators = DDDecoratorsFactory.createFromResources();
    for (final DDSpanContextDecorator decorator : decorators) {
      tracer.addDecorator(decorator);
    }

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
