package datadog.trace.instrumentation.grpc.server;

import datadog.trace.agent.decorator.ServerDecorator;
import datadog.trace.api.DDSpanTypes;
import io.grpc.Status;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

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

  public Span onClose(final Span span, final Status status) {

    span.setTag("status.code", status.getCode().name());
    span.setTag("status.description", status.getDescription());

    onError(span, status.getCause());
    if (!status.isOk()) {
      Tags.ERROR.set(span, true);
    }

    return span;
  }
}
