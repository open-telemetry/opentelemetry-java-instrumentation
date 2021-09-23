/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_4.trace;

import application.io.opentelemetry.api.trace.Tracer;
import application.io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;

class ApplicationTracerBuilder implements TracerBuilder {

  private final io.opentelemetry.api.trace.TracerBuilder agentTracerBuilder;

  public ApplicationTracerBuilder(io.opentelemetry.api.trace.TracerBuilder agentTracerBuilder) {
    this.agentTracerBuilder = agentTracerBuilder;
  }

  @Override
  public TracerBuilder setSchemaUrl(String s) {
    agentTracerBuilder.setSchemaUrl(s);
    return this;
  }

  @Override
  public TracerBuilder setInstrumentationVersion(String s) {
    agentTracerBuilder.setSchemaUrl(s);
    return this;
  }

  @Override
  public Tracer build() {
    return new ApplicationTracer(agentTracerBuilder.build());
  }
}
