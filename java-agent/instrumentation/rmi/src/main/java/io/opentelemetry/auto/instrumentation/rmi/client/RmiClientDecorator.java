package io.opentelemetry.auto.instrumentation.rmi.client;

import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ClientDecorator;

public class RmiClientDecorator extends ClientDecorator {
  public static final RmiClientDecorator DECORATE = new RmiClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"rmi", "rmi-client"};
  }

  @Override
  protected String spanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "rmi-client";
  }

  @Override
  protected String service() {
    return null;
  }
}
