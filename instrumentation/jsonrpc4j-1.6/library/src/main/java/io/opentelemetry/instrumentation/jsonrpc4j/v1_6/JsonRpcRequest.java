package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import java.lang.reflect.Method;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public final class JsonRpcRequest {

  private final Method method;
  private final List<JsonNode> arguments;

  JsonRpcRequest(Method method, List<JsonNode> arguments) {
    this.method = method;
    this.arguments = arguments;
  }


  public Method getMethod() {
    return method;
  }

  public List<JsonNode> getArguments() {
    return arguments;
  }


}
