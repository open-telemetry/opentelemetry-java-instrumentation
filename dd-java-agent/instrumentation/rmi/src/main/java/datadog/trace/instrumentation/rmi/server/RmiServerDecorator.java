package datadog.trace.instrumentation.rmi.server;

import datadog.trace.agent.decorator.ServerDecorator;
import datadog.trace.api.DDSpanTypes;

public class RmiServerDecorator extends ServerDecorator {
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi"};
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
