/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.OpenTelemetryConfigurable;

/** A {@link SpanNameExtractor} for RPC requests. */
public final class RpcSpanNameExtractor<REQUEST>
    implements SpanNameExtractor<REQUEST>, OpenTelemetryConfigurable {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to RPC semantic
   * conventions.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      RpcAttributesGetter<REQUEST, ?> attributesExtractor) {
    return new RpcSpanNameExtractor<>(attributesExtractor);
  }

  private final RpcAttributesGetter<REQUEST, ?> getter;
  private final RpcSemconvSelection selection = new RpcSemconvSelection();

  private RpcSpanNameExtractor(RpcAttributesGetter<REQUEST, ?> getter) {
    this.getter = getter;
  }

  @Override
  public void internalInitialize(OpenTelemetry openTelemetry) {
    selection.initialize(openTelemetry);
  }

  @SuppressWarnings("deprecation") // for getMethod()
  @Override
  public String extract(REQUEST request) {
    if (selection.get().emitPreview()) {
      String method = getter.getRpcMethod(request);
      if (method != null && !method.equals("_OTHER")) {
        return method;
      }
      // fall back to rpc.system.name
      return getter.getRpcSystemName(request);
    }

    String service = getter.getService(request);
    String method = getter.getMethod(request);
    if (service == null || method == null) {
      return "RPC request";
    }
    return service + '/' + method;
  }
}
