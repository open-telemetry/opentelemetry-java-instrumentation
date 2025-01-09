/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.lang.reflect.Method;

public class JsonRpcServerSpanNameExtractor implements SpanNameExtractor<JsonRpcRequest> {
  // Follow https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/#span-name
  @Override
  public String extract(JsonRpcRequest request) {
    Method method = request.getMethod();
    return String.format("%s/%s", method.getDeclaringClass().getName(), method.getName());
  }
}
