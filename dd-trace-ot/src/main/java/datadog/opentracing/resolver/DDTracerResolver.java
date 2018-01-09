package datadog.opentracing.resolver;

import com.google.auto.service.AutoService;
import datadog.opentracing.DDTracer;
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver {

  public static Tracer registerTracer() {
    final Tracer tracer = TracerResolver.resolveTracer();

    if (tracer == null) {
      log.warn("Cannot resolved the tracer, use NoopTracer");
      return NoopTracerFactory.create();
    }

    log.info("Register the tracer via GlobalTracer");
    GlobalTracer.register(tracer);
    return tracer;
  }

  @Override
  protected Tracer resolve() {
    log.info("Creating the Datadog Tracer from the resolver");

    return new DDTracer();
  }
}
