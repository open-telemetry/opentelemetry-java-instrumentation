package io.opentelemetry.auto.instrumentation.rmi.client;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;

public class RmiClientDecorator extends ClientDecorator {
  public static final RmiClientDecorator DECORATE = new RmiClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String getSpanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String getComponentName() {
    return "rmi-client";
  }

  @Override
  protected String service() {
    return null;
  }
}
