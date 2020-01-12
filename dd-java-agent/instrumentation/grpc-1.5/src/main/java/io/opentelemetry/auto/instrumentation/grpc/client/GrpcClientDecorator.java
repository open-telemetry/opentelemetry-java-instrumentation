package io.opentelemetry.auto.instrumentation.grpc.client;

import io.grpc.Status;
import io.opentelemetry.auto.agent.decorator.ClientDecorator;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;

public class GrpcClientDecorator extends ClientDecorator {
  public static final GrpcClientDecorator DECORATE = new GrpcClientDecorator();

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

  public AgentSpan onClose(final AgentSpan span, final Status status) {

    span.setTag("status.code", status.getCode().name());
    span.setTag("status.description", status.getDescription());

    onError(span, status.getCause());
    if (!status.isOk()) {
      span.setError(true);
    }

    return span;
  }
}
