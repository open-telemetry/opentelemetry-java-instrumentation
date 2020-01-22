package io.opentelemetry.auto.instrumentation.servlet.http;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.trace.Tracer;

public class HttpServletResponseDecorator extends BaseDecorator {
  public static final HttpServletResponseDecorator DECORATE = new HttpServletResponseDecorator();
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-response"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "java-web-servlet-response";
  }
}
