/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.trace.Tracer;
import application.io.opentelemetry.api.trace.TracerBuilder;
import application.io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.GlobalOpenTelemetry;

public class ApplicationTracerProvider implements TracerProvider {

  private final io.opentelemetry.api.trace.TracerProvider agentTracerProvider;

  public ApplicationTracerProvider(
      io.opentelemetry.api.trace.TracerProvider applicationOriginalTracerProvider) {
    this.agentTracerProvider = applicationOriginalTracerProvider;
  }

  @Override
  public Tracer get(String instrumentationName) {
    return new ApplicationTracer(agentTracerProvider.get(instrumentationName));
  }

  @Override
  public Tracer get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationTracer(
        GlobalOpenTelemetry.getTracerProvider().get(instrumentationName, instrumentationVersion));
  }

  @Override
  public TracerBuilder tracerBuilder(String instrumentationName) {
    return new ApplicationTracerBuilder(agentTracerProvider.tracerBuilder(instrumentationName));
  }
}
