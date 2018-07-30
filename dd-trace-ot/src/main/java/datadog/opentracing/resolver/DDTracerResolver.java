package datadog.opentracing.resolver;

import com.google.auto.service.AutoService;
import datadog.opentracing.DDTracer;
import datadog.trace.common.util.Config;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoService(TracerResolver.class)
public class DDTracerResolver extends TracerResolver {
  static final String CONFIG_KEY = "dd.trace.resolver.enabled";

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
    final boolean enabled = !"false".equalsIgnoreCase(Config.getPropOrEnv(CONFIG_KEY));
    if (enabled) {
      log.info("Creating DDTracer with DDTracerResolver");
      return new DDTracer();
    } else {
      log.info("DDTracerResolver disabled");
      return null;
    }
  }
}
