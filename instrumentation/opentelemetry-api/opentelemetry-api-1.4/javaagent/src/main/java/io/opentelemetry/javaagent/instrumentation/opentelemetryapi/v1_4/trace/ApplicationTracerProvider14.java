/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace;

import application.io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerProvider;

// this class is used from opentelemetry-api-1.0 via reflection
public class ApplicationTracerProvider14 extends ApplicationTracerProvider {

  // Our convention for accessing agent package
  @SuppressWarnings("UnnecessarilyFullyQualified")
  public ApplicationTracerProvider14(
      io.opentelemetry.api.trace.TracerProvider agentTracerProvider) {
    super(agentTracerProvider);
  }

  @Override
  public TracerBuilder tracerBuilder(String instrumentationName) {
    return new ApplicationTracerBuilder(agentTracerProvider.tracerBuilder(instrumentationName));
  }
}
