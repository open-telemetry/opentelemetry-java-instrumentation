/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.lang.reflect.Method;

public class JsonRpcClientSpanNameExtractor implements SpanNameExtractor<SimpleJsonRpcRequest> {
  @Override
  public String extract(SimpleJsonRpcRequest request) {
    if (request.getMethod() == null) {
      return request.getMethodName();
    }
    Method method = request.getMethod();
    return String.format("%s/%s", method.getDeclaringClass().getName(), method.getName());
  }
}
