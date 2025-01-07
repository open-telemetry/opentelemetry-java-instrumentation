package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import com.googlecode.jsonrpc4j.JsonRpcService;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;

// Check https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md#attributes
// Check https://opentelemetry.io/docs/specs/semconv/rpc/json-rpc/
public enum JsonRpcServerAttributesGetter implements RpcAttributesGetter<JsonRpcRequest> {
  INSTANCE;

  @Override
  public String getSystem(JsonRpcRequest request) {
    return "jsonrpc";
  }

  @Override
  public String getService(JsonRpcRequest request) {
    return request.getMethod().getDeclaringClass().getAnnotation(JsonRpcService.class).value();
  }

  @Override
  public String getMethod(JsonRpcRequest request) {
    return request.getMethod().getName();
  }
}
