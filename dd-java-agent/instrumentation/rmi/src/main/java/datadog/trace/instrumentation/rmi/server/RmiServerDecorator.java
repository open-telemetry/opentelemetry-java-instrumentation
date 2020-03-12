package datadog.trace.instrumentation.rmi.server;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.bootstrap.instrumentation.decorator.ServerDecorator;

public class RmiServerDecorator extends ServerDecorator {
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi", "rmi-server"};
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "rmi-server";
  }
}
