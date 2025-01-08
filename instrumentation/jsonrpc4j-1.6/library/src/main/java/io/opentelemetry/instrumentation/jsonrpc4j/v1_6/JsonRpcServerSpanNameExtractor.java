/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_6;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class JsonRpcServerSpanNameExtractor implements SpanNameExtractor<JsonRpcRequest> {
  @Override
  public String extract(JsonRpcRequest request) {
    return request.getMethod().getName();
  }
}
