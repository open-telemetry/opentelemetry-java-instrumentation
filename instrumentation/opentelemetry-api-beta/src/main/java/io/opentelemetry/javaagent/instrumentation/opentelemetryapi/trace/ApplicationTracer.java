/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

class ApplicationTracer implements Tracer {

  private final io.opentelemetry.trace.Tracer agentTracer;
  private final ContextStore<Context, io.opentelemetry.context.Context> contextStore;

  ApplicationTracer(
      io.opentelemetry.trace.Tracer agentTracer,
      ContextStore<Context, io.opentelemetry.context.Context> contextStore) {
    this.agentTracer = agentTracer;
    this.contextStore = contextStore;
  }

  @Override
  public Span.Builder spanBuilder(String spanName) {
    return new ApplicationSpan.Builder(agentTracer.spanBuilder(spanName), contextStore);
  }
}
