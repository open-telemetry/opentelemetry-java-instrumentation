package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class JsonRpcClientSpanNameExtractor implements SpanNameExtractor<SimpleJsonRpcRequest> {
  @Override
  public String extract(SimpleJsonRpcRequest request) {
    return request.getMethodName();
  }
}
