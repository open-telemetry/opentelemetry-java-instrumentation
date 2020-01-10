package datadog.trace.instrumentation.jdbc;

import datadog.trace.agent.decorator.BaseDecorator;

public class DataSourceDecorator extends BaseDecorator {
  public static final DataSourceDecorator DECORATE = new DataSourceDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jdbc-datasource"};
  }

  @Override
  protected String component() {
    return "java-jdbc-connection";
  }

  @Override
  protected String spanType() {
    return null;
  }
}
