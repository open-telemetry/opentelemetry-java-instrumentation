/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.metrics.MeterProvider;
import application.io.opentelemetry.api.trace.TracerProvider;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.propagation.ApplicationContextPropagators;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeterProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace.ApplicationTracerProvider14;

public final class ApplicationOpenTelemetry110 implements OpenTelemetry {

  // Accessed with reflection
  @SuppressWarnings("unused")
  public static final OpenTelemetry INSTANCE = new ApplicationOpenTelemetry110();

  private final TracerProvider applicationTracerProvider;
  private final ContextPropagators applicationContextPropagators;
  private final MeterProvider applicationMeterProvider;

  @SuppressWarnings("UnnecessarilyFullyQualified")
  private ApplicationOpenTelemetry110() {
    io.opentelemetry.api.OpenTelemetry agentOpenTelemetry =
        io.opentelemetry.api.GlobalOpenTelemetry.get();
    applicationTracerProvider =
        new ApplicationTracerProvider14(agentOpenTelemetry.getTracerProvider());
    applicationContextPropagators =
        new ApplicationContextPropagators(agentOpenTelemetry.getPropagators());
    applicationMeterProvider = new ApplicationMeterProvider(agentOpenTelemetry.getMeterProvider());
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
