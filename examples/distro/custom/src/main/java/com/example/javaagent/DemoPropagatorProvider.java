package com.example.javaagent;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;

public class DemoPropagatorProvider implements ConfigurablePropagatorProvider {
  @Override
  public TextMapPropagator getPropagator() {
    return new DemoPropagator();
  }

  @Override
  public String getName() {
    return "demo";
  }
}
