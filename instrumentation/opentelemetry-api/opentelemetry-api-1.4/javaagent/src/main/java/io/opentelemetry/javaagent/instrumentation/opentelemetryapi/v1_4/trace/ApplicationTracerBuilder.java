/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracerFactory;

class ApplicationTracerBuilder implements application.io.opentelemetry.api.trace.TracerBuilder {

  private final ApplicationTracerFactory tracerFactory;
  private final TracerBuilder agentTracerBuilder;

  public ApplicationTracerBuilder(
      ApplicationTracerFactory tracerFactory, TracerBuilder agentTracerBuilder) {
    this.tracerFactory = tracerFactory;
    this.agentTracerBuilder = agentTracerBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.TracerBuilder setSchemaUrl(String schemaUrl) {
    agentTracerBuilder.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.TracerBuilder setInstrumentationVersion(
      String version) {
    agentTracerBuilder.setInstrumentationVersion(version);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.trace.Tracer build() {
    return tracerFactory.newTracer(agentTracerBuilder.build());
  }
}
