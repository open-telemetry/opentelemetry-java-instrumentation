/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grpc.server;

import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Builder;
import io.opentelemetry.trace.attributes.SemanticAttributes;

public class GrpcServerTracer extends RpcServerTracer<Metadata> {
  public static final GrpcServerTracer TRACER = new GrpcServerTracer();

  public Span startSpan(String name, Metadata headers) {
    Builder spanBuilder =
        tracer.spanBuilder(name).setSpanKind(SERVER).setParent(extract(headers, getGetter()));
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
    return spanBuilder.startSpan();
  }

  public void setStatus(Span span, Status status) {
    span.setStatus(GrpcHelper.statusFromGrpcStatus(status), status.getDescription());
    if (status.getCause() != null) {
      addThrowable(span, status.getCause());
    }
  }

  @Override
  protected void onError(Span span, Throwable throwable) {
    Status grpcStatus = Status.fromThrowable(throwable);
    super.onError(span, grpcStatus.getCause());
    span.setStatus(GrpcHelper.statusFromGrpcStatus(grpcStatus), grpcStatus.getDescription());
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grpc-1.5";
  }

  @Override
  protected Getter<Metadata> getGetter() {
    return GrpcExtractAdapter.GETTER;
  }
}
