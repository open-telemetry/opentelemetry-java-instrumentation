package datadog.trace.instrumentation.rmi.server;

import datadog.trace.agent.decorator.ServerDecorator;
import datadog.trace.api.SpanTypes;

public class RmiServerDecorator extends ServerDecorator {
  public static final RmiServerDecorator DECORATE = new RmiServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi"};
  }

  @Override
  protected String spanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "rmi-server";
  }
}
