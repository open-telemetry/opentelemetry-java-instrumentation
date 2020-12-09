/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.client;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.instrumentation.grpc.v1_5.common.GrpcHelper;

public class GrpcClientTracer extends RpcClientTracer {

  protected GrpcClientTracer() {}

  protected GrpcClientTracer(Tracer tracer) {
    super(tracer);
  }

  public Span startSpan(String name) {
    SpanBuilder spanBuilder = tracer.spanBuilder(name).setSpanKind(CLIENT);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
    return spanBuilder.startSpan();
  }

  public void endSpan(Span span, Status status) {
    span.setStatus(GrpcHelper.statusFromGrpcStatus(status), status.getDescription());
    end(span);
  }

  @Override
  protected void onException(Span span, Throwable throwable) {
    Status grpcStatus = Status.fromThrowable(throwable);
    super.onException(span, grpcStatus.getCause());
    span.setStatus(GrpcHelper.statusFromGrpcStatus(grpcStatus), grpcStatus.getDescription());
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.grpc";
  }
}
