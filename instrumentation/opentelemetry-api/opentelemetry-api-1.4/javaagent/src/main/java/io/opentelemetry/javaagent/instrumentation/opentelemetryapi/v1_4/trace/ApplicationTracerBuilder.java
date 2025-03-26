/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace;

import application.io.opentelemetry.api.trace.Tracer;
import application.io.opentelemetry.api.trace.TracerBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerFactory;

class ApplicationTracerBuilder implements TracerBuilder {

  private final ApplicationTracerFactory tracerFactory;
  private final io.opentelemetry.api.trace.TracerBuilder agentTracerBuilder;

  public ApplicationTracerBuilder(
      ApplicationTracerFactory tracerFactory,
      io.opentelemetry.api.trace.TracerBuilder agentTracerBuilder) {
    this.tracerFactory = tracerFactory;
    this.agentTracerBuilder = agentTracerBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public TracerBuilder setSchemaUrl(String schemaUrl) {
    agentTracerBuilder.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public TracerBuilder setInstrumentationVersion(String version) {
    agentTracerBuilder.setInstrumentationVersion(version);
    return this;
  }

  @Override
  public Tracer build() {
    return tracerFactory.newTracer(agentTracerBuilder.build());
  }
}
