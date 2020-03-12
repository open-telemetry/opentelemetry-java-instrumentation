package datadog.trace.instrumentation.servlet.http;

import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;

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
