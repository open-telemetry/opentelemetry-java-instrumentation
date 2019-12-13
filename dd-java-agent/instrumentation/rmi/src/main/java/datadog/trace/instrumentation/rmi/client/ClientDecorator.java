package datadog.trace.instrumentation.rmi.client;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.api.DDSpanTypes;

public class ClientDecorator extends BaseDecorator {
  public static final ClientDecorator DECORATE = new ClientDecorator();

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
    return "rmi-client";
  }
}
