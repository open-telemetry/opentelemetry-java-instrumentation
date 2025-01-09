/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.util.List;

public final class JsonRpcResponse {
  private final Method method;
  private final List<JsonNode> params;
  private final Object result;

  JsonRpcResponse(Method method, List<JsonNode> params, Object result) {
    this.method = method;
    this.params = params;
    this.result = result;
  }

  public Method getMethod() {
    return method;
  }

  public List<JsonNode> getParams() {
    return params;
  }

  public Object getResult() {
    return result;
  }
}
