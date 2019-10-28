package datadog.trace.instrumentation.servlet.filter;

import datadog.trace.agent.decorator.BaseDecorator;

public class FilterDecorator extends BaseDecorator {
  public static final FilterDecorator DECORATE = new FilterDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-filter"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "java-web-servlet-filter";
  }
}
