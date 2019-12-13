package datadog.trace.instrumentation.rmi.server;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.api.DDSpanTypes;

public class ServerDecorator extends BaseDecorator {
  public static final ServerDecorator DECORATE = new ServerDecorator();

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
