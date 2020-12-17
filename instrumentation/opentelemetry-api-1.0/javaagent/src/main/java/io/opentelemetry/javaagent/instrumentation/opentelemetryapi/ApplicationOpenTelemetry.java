/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.metrics.MeterProvider;
import application.io.opentelemetry.api.trace.TracerProvider;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation.ApplicationContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.ApplicationMeterProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerProvider;

public class ApplicationOpenTelemetry implements OpenTelemetry {

  public static final OpenTelemetry INSTANCE = new ApplicationOpenTelemetry();

  private final TracerProvider applicationTracerProvider;
  private final MeterProvider applicationMeterProvider;

  private final ContextPropagators applicationContextPropagators;

  private ApplicationOpenTelemetry() {
    io.opentelemetry.api.OpenTelemetry agentOpenTelemetry =
        io.opentelemetry.api.OpenTelemetry.get();
    applicationTracerProvider =
        new ApplicationTracerProvider(agentOpenTelemetry.getTracerProvider());
    applicationMeterProvider = new ApplicationMeterProvider(agentOpenTelemetry.getMeterProvider());
    applicationContextPropagators =
        new ApplicationContextPropagators(agentOpenTelemetry.getPropagators());
  }

  @Override
  public void setPropagators(ContextPropagators contextPropagators) {
    // TODO(anuraaga): Implement this somehow.
  }

  @Override
  public TracerProvider getTracerProvider() {
    return applicationTracerProvider;
  }

  @Override
  public MeterProvider getMeterProvider() {
    return applicationMeterProvider;
  }

  @Override
  public ContextPropagators getPropagators() {
    return applicationContextPropagators;
  }
}
