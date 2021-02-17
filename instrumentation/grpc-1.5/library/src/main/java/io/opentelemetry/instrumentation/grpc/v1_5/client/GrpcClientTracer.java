/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.client;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.instrumentation.grpc.v1_5.common.GrpcHelper;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class GrpcClientTracer extends RpcClientTracer {

  protected GrpcClientTracer() {}

  protected GrpcClientTracer(Tracer tracer) {
    super(tracer);
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
    return "io.opentelemetry.javaagent.grpc";
  }
}
