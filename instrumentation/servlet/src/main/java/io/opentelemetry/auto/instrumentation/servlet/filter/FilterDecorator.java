package io.opentelemetry.auto.instrumentation.servlet.filter;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class FilterDecorator extends BaseDecorator {
  public static final FilterDecorator DECORATE = new FilterDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet-filter"};
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
