/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.trace.SpanBuilder;
import application.io.opentelemetry.api.trace.Tracer;

public class ApplicationTracer implements Tracer {

  private final io.opentelemetry.api.trace.Tracer agentTracer;

  public ApplicationTracer(io.opentelemetry.api.trace.Tracer agentTracer) {
    this.agentTracer = agentTracer;
  }

  @Override
  public SpanBuilder spanBuilder(String spanName) {
    return new ApplicationSpan.Builder(agentTracer.spanBuilder(spanName));
  }
}
