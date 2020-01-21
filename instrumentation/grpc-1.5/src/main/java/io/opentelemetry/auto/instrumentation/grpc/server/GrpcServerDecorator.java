package io.opentelemetry.auto.instrumentation.grpc.server;

import io.grpc.Status;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ServerDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class GrpcServerDecorator extends ServerDecorator {
  public static final GrpcServerDecorator DECORATE = new GrpcServerDecorator();
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"grpc", "grpc-server"};
  }

  @Override
  protected String spanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "grpc-server";
  }

  public Span onClose(final Span span, final Status status) {

    span.setAttribute("status.code", status.getCode().name());
    if (status.getDescription() != null) {
      span.setAttribute("status.description", status.getDescription());
    }
    onError(span, status.getCause());
    if (!status.isOk()) {
      span.setStatus(io.opentelemetry.trace.Status.UNKNOWN);
    }
    return span;
  }
}
