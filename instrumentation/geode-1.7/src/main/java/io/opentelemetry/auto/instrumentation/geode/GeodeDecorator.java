package io.opentelemetry.auto.instrumentation.geode;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;
import org.apache.geode.cache.Cache;

public class GeodeDecorator extends DatabaseClientDecorator<Cache> {
  public static GeodeDecorator DECORATE = new GeodeDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.geode-1.7");

  @Override
  protected String dbType() {
    return "geode";
  }

  @Override
  protected String dbUser(final Cache cache) {
    return null;
  }

  @Override
  protected String dbInstance(final Cache cache) {
    return cache.getName();
  }

  @Override
  protected String service() {
    return "apache-geode";
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.CACHE;
  }

  @Override
  protected String getComponentName() {
    return "apache-geode";
  }
}
