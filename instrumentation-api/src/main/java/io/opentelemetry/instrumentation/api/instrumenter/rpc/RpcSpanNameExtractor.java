/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/** A {@link SpanNameExtractor} for RPC requests. */
public final class RpcSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to RPC semantic
   * conventions: {@code <rpc.service>/<rpc.method>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      RpcAttributesExtractor<REQUEST, ?> attributesExtractor) {
    return new RpcSpanNameExtractor<>(attributesExtractor);
  }

  private final RpcAttributesExtractor<REQUEST, ?> attributesExtractor;

  private RpcSpanNameExtractor(RpcAttributesExtractor<REQUEST, ?> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public String extract(REQUEST request) {
    String service = attributesExtractor.service(request);
    String method = attributesExtractor.method(request);
    if (service == null || method == null) {
      return "RPC request";
    }
    return service + '/' + method;
  }
}
