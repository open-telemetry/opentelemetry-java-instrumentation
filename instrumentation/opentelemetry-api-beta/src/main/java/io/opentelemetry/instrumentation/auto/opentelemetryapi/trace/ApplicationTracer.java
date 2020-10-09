/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.trace;

import application.io.grpc.Context;
import application.io.opentelemetry.context.Scope;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

class ApplicationTracer implements Tracer {

  private final io.opentelemetry.trace.Tracer agentTracer;
  private final ContextStore<Context, io.grpc.Context> contextStore;

  ApplicationTracer(
      io.opentelemetry.trace.Tracer agentTracer,
      ContextStore<Context, io.grpc.Context> contextStore) {
    this.agentTracer = agentTracer;
    this.contextStore = contextStore;
  }

  @Override
  public Span getCurrentSpan() {
    return Bridging.toApplication(agentTracer.getCurrentSpan());
  }

  @Override
  public Scope withSpan(Span applicationSpan) {
    return TracingContextUtils.currentContextWith(applicationSpan);
  }

  @Override
  public Span.Builder spanBuilder(String spanName) {
    return new ApplicationSpan.Builder(agentTracer.spanBuilder(spanName), contextStore);
  }
}
