package io.opentelemetry.auto.instrumentation.vertx;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.trace.Tracer;

public class VertxDecorator extends BaseDecorator {
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.vertx");
  
}
