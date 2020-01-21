package io.opentelemetry.auto.instrumentation.servlet.http;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class HttpServletDecorator extends BaseDecorator {
  public static final HttpServletDecorator DECORATE = new HttpServletDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet-service"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "java-web-servlet-service";
  }
}
