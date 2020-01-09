package datadog.trace.instrumentation.servlet.dispatcher;

import datadog.trace.agent.decorator.BaseDecorator;

public class RequestDispatcherDecorator extends BaseDecorator {
  public static final RequestDispatcherDecorator DECORATE = new RequestDispatcherDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet-beta", "servlet-dispatcher"};
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
