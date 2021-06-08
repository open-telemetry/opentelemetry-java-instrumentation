/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

// Small optimization to avoid RpcSpanNameExtractor because gRPC provides the span name directly.
final class GrpcSpanNameExtractor implements SpanNameExtractor<GrpcRequest> {
  @Override
  public String extract(GrpcRequest request) {
    return request.getMethod().getFullMethodName();
  }
}
