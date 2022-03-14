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
      RpcClientAttributesGetter<REQUEST> getter) {
    return new RpcSpanNameExtractor<>(getter);
  }

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to RPC semantic
   * conventions: {@code <rpc.service>/<rpc.method>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      RpcServerAttributesGetter<REQUEST> getter) {
    return new RpcSpanNameExtractor<>(getter);
  }

  private final RpcCommonAttributesGetter<REQUEST> getter;

  private RpcSpanNameExtractor(RpcCommonAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  @Override
  public String extract(REQUEST request) {
    String service = getter.service(request);
    String method = getter.method(request);
    if (service == null || method == null) {
      return "RPC request";
    }
    return service + '/' + method;
  }
}
