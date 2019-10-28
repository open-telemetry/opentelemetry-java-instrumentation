package datadog.trace.instrumentation.servlet.http;

import datadog.trace.agent.decorator.BaseDecorator;

public class HttpServletResponseDecorator extends BaseDecorator {
  public static final HttpServletResponseDecorator DECORATE = new HttpServletResponseDecorator();

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
