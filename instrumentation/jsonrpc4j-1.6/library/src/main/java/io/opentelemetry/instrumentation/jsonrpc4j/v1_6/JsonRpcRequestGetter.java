package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import java.util.ArrayList;

enum JsonRpcRequestGetter implements TextMapGetter<JsonRpcRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(JsonRpcRequest request) {
    return new ArrayList<>();
  }

  @Override
  @Nullable
  public String get(@Nullable JsonRpcRequest request, String key) {
    return null;
  }
}
