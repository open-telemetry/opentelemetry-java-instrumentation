package datadog.trace.instrumentation.grpc.client;

import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.grpc.Status;

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
    return DDSpanTypes.RPC;
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
