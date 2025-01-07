package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

public final class SimpleJsonRpcResponse {

  private final Object result;


  public SimpleJsonRpcResponse(Object result) {
    this.result = result;
  }

  public Object getResult() {
    return result;
  }
}
