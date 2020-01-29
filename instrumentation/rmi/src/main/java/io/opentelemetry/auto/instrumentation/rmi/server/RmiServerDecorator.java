package io.opentelemetry.auto.instrumentation.rmi.server;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ServerDecorator;
import io.opentelemetry.trace.Tracer;

public class RmiServerDecorator extends ServerDecorator {
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi", "rmi-server"};
  }

  @Override
  protected String spanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "rmi-server";
  }
}
