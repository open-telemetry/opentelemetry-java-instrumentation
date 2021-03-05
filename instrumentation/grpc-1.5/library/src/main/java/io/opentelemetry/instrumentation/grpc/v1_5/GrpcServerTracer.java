/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

final class GrpcServerTracer extends RpcServerTracer<Metadata> {

  GrpcServerTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public Context startSpan(String name, Metadata headers) {
    SpanBuilder spanBuilder =
        tracer.spanBuilder(name).setSpanKind(SERVER).setParent(extract(headers, getGetter()));
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
    return Context.current().with(spanBuilder.startSpan());
  }

  public void setStatus(Context context, Status status) {
    Span span = Span.fromContext(context);
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
    return "io.opentelemetry.javaagent.grpc-1.5";
  }

  @Override
  protected TextMapGetter<Metadata> getGetter() {
    return GrpcExtractAdapter.GETTER;
  }
}
