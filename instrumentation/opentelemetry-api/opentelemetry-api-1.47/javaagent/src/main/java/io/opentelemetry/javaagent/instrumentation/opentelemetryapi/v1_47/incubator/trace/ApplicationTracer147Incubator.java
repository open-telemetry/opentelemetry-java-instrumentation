/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator.trace;

import application.io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import application.io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace.ApplicationSpanBuilder140Incubator;

final class ApplicationTracer147Incubator extends ApplicationTracer implements ExtendedTracer {

  private final Tracer agentTracer;

  ApplicationTracer147Incubator(Tracer agentTracer) {
    super(agentTracer);
    this.agentTracer = agentTracer;
  }

  @Override
  public ExtendedSpanBuilder spanBuilder(String spanName) {
    return new ApplicationSpanBuilder140Incubator(agentTracer.spanBuilder(spanName));
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.trace.ExtendedTracer) agentTracer).isEnabled();
  }
}
