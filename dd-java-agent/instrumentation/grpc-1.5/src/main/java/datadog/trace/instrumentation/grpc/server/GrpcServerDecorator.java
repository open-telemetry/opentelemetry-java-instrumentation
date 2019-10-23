package datadog.trace.instrumentation.grpc.server;

import datadog.trace.agent.decorator.ServerDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.instrumentation.api.AgentSpan;
import io.grpc.Status;

public class GrpcServerDecorator extends ServerDecorator {
  public static final GrpcServerDecorator DECORATE = new GrpcServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"grpc", "grpc-server"};
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.RPC;
  }

  @Override
  protected String component() {
    return "grpc-server";
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
