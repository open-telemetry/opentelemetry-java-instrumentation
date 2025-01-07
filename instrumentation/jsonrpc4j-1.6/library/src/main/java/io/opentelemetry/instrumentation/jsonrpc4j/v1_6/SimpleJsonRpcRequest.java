package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;


public final class SimpleJsonRpcRequest {

  private final String methodName;
  private final Object argument;

  public SimpleJsonRpcRequest(String methodName, Object argument) {
    this.methodName = methodName;
    this.argument = argument;
  }

  public String getMethodName() {
    return methodName;
  }

  public Object getArgument() {
    return argument;
  }
}
