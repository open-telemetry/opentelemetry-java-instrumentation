package io.opentelemetry.auto.instrumentation.rmi.server;

import io.opentelemetry.auto.agent.decorator.ServerDecorator;
import io.opentelemetry.auto.api.SpanTypes;

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
