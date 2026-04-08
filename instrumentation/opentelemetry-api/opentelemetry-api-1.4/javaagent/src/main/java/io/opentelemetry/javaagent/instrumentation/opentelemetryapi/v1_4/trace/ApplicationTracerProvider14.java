/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace;

import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerFactory;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerProvider;

// this class is used from opentelemetry-api-1.0 via reflection
public class ApplicationTracerProvider14 extends ApplicationTracerProvider {
  public ApplicationTracerProvider14(TracerProvider agentTracerProvider) {
    this(ApplicationTracer::new, agentTracerProvider);
  }

  public ApplicationTracerProvider14(
      ApplicationTracerFactory tracerFactory, TracerProvider agentTracerProvider) {
    super(tracerFactory, agentTracerProvider);
  }

  @Override
  public application.io.opentelemetry.api.trace.TracerBuilder tracerBuilder(
      String instrumentationName) {
    return new ApplicationTracerBuilder(
        tracerFactory, agentTracerProvider.tracerBuilder(instrumentationName));
  }
}
