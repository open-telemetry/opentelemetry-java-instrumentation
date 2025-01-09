/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class JsonRpcClientSpanNameExtractor implements SpanNameExtractor<SimpleJsonRpcRequest> {
  @Override
  public String extract(SimpleJsonRpcRequest request) {
    return request.getMethodName();
  }
}
