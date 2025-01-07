package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;

// Check https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#attributes
// Check https://opentelemetry.io/docs/specs/semconv/rpc/json-rpc/
public enum JsonRpcClientAttributesGetter implements RpcAttributesGetter<SimpleJsonRpcRequest> {
  INSTANCE;

  @Override
  public String getSystem(SimpleJsonRpcRequest request) {
    return "jsonrpc";
  }

  @Override
  public String getService(SimpleJsonRpcRequest request) {
    // TODO
    return "NOT_IMPLEMENTED";
  }

  @Override
  public String getMethod(SimpleJsonRpcRequest request) {
    return request.getMethodName();
  }
}
