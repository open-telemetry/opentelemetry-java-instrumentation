package io.opentelemetry.auto.instrumentation.trace_annotation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Tracer;

public class TraceDecorator extends BaseDecorator {
  public static TraceDecorator DECORATE = new TraceDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "trace";
  }
}
