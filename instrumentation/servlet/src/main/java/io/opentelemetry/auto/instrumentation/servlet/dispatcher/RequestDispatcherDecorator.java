package io.opentelemetry.auto.instrumentation.servlet.dispatcher;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Tracer;

public class RequestDispatcherDecorator extends BaseDecorator {
  public static final RequestDispatcherDecorator DECORATE = new RequestDispatcherDecorator();
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-dispatcher"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "java-web-servlet-dispatcher";
  }
}
