/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
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
    Context parentContext = Context.current();
    Span span =
        spanBuilder(parentContext, name, CLIENT)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, getRpcSystem())
            .startSpan();
    // TODO: withClientSpan()
    return parentContext.with(span);
  }

  public void end(Context context, Status status) {
    StatusCode statusCode = GrpcHelper.statusFromGrpcStatus(status);
    if (statusCode != StatusCode.UNSET) {
      Span.fromContext(context).setStatus(statusCode, status.getDescription());
    }
    end(context);
  }

  @Override
  public void onException(Context context, Throwable throwable) {
    Status grpcStatus = Status.fromThrowable(throwable);
    Span span = Span.fromContext(context);
    StatusCode statusCode = GrpcHelper.statusFromGrpcStatus(grpcStatus);
    if (statusCode != StatusCode.UNSET) {
      span.setStatus(statusCode, grpcStatus.getDescription());
    }
    span.recordException(unwrapThrowable(grpcStatus.getCause()));
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.grpc-1.5";
  }
}
