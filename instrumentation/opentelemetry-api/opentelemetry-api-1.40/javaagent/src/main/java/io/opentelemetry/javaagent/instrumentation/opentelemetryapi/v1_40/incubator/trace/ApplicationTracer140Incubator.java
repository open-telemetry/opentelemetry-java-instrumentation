/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace;

import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationTracer;

final class ApplicationTracer140Incubator extends ApplicationTracer
    implements application.io.opentelemetry.api.incubator.trace.ExtendedTracer {

  private final Tracer agentTracer;

  ApplicationTracer140Incubator(Tracer agentTracer) {
    super(agentTracer);
    this.agentTracer = agentTracer;
  }

  @Override
  public application.io.opentelemetry.api.trace.SpanBuilder spanBuilder(String spanName) {
    return new ApplicationSpanBuilder140Incubator(agentTracer.spanBuilder(spanName));
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedTracer) agentTracer).isEnabled();
  }
}
