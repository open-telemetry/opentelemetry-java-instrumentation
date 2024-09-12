/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace;

import application.io.opentelemetry.api.incubator.trace.ExtendedTracer;
import application.io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;

final class ApplicationTracer140Incubator extends ApplicationTracer implements ExtendedTracer {

  private final io.opentelemetry.api.trace.Tracer agentTracer;

  ApplicationTracer140Incubator(Tracer agentTracer) {
    super(agentTracer);
    this.agentTracer = agentTracer;
  }

  @Override
  public SpanBuilder spanBuilder(String spanName) {
    return new ApplicationSpanBuilder140Incubator(agentTracer.spanBuilder(spanName));
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.trace.ExtendedTracer) agentTracer).isEnabled();
  }
}
