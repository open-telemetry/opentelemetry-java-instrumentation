package io.opentelemetry.auto.instrumentation.grpc.client;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;

public class GrpcClientDecorator extends ClientDecorator {
  public static final GrpcClientDecorator DECORATE = new GrpcClientDecorator();
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"grpc", "grpc-client"};
  }

  @Override
  protected String component() {
    return "grpc-client";
  }

  @Override
  protected String spanType() {
    return SpanTypes.RPC;
  }

  @Override
  protected String service() {
    return null;
  }

  public Span onClose(final Span span, final io.grpc.Status status) {

    span.setAttribute("status.code", status.getCode().name());
    if (status.getDescription() != null) {
      span.setAttribute("status.description", status.getDescription());
    }

    onError(span, status.getCause());
    if (!status.isOk()) {
      span.setStatus(Status.UNKNOWN);
    }
    return span;
  }
}
