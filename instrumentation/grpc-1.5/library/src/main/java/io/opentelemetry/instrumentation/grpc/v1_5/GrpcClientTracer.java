/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

final class GrpcClientTracer extends RpcClientTracer {

  GrpcClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  @Override
  protected String getRpcSystem() {
    return "grpc";
  }

  public Context startSpan(String name) {
    Span span =
        spanBuilder(name, CLIENT)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, getRpcSystem())
            .startSpan();
    return Context.current().with(span);
  }

  public void end(Context context, Status status) {
    Span.fromContext(context)
        .setStatus(GrpcHelper.statusFromGrpcStatus(status), status.getDescription());
    end(context);
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
}
