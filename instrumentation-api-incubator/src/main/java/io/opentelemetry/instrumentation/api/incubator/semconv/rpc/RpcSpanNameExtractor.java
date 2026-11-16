/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/** A {@link SpanNameExtractor} for RPC requests. */
public final class RpcSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to RPC semantic
   * conventions.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      RpcAttributesGetter<REQUEST, ?> attributesExtractor) {
    return new RpcSpanNameExtractor<>(attributesExtractor);
  }

  private final RpcAttributesGetter<REQUEST, ?> getter;

  private RpcSpanNameExtractor(RpcAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String method = getter.getRpcMethod(request);
    if (method != null && !method.equals("_OTHER")) {
      return method;
    }
    return getter.getRpcSystemName(request);
  }
}
