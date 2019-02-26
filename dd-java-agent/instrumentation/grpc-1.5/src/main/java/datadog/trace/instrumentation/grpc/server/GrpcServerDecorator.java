package datadog.trace.instrumentation.grpc.server;

import datadog.trace.agent.decorator.ServerDecorator;
import datadog.trace.api.DDSpanTypes;

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
}
