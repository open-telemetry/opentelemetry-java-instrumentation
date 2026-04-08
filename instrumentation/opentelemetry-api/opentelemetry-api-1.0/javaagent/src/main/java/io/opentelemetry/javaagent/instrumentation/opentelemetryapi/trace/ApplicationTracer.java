/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import io.opentelemetry.api.trace.Tracer;

public class ApplicationTracer implements application.io.opentelemetry.api.trace.Tracer {

  private final Tracer agentTracer;

  public ApplicationTracer(Tracer agentTracer) {
    this.agentTracer = agentTracer;
  }

  @Override
  public application.io.opentelemetry.api.trace.SpanBuilder spanBuilder(String spanName) {
    return new ApplicationSpanBuilder(agentTracer.spanBuilder(spanName));
  }
}
