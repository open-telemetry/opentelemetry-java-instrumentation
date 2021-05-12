/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

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
    Context parentContext = extract(headers, getGetter());
    SpanBuilder spanBuilder = spanBuilder(parentContext, name, SERVER);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
    // TODO: withServerSpan()
    return parentContext.with(spanBuilder.startSpan());
  }

  public void setStatus(Context context, Status status) {
    Span span = Span.fromContext(context);
    span.setStatus(GrpcHelper.statusFromGrpcStatus(status), status.getDescription());
    if (status.getCause() != null) {
      span.recordException(unwrapThrowable(status.getCause()));
    }
  }

  @Override
  public void onException(Context context, Throwable throwable) {
    Status grpcStatus = Status.fromThrowable(throwable);
    setStatus(context, grpcStatus);
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
