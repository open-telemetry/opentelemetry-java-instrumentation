/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/** A {@link SpanNameExtractor} for RPC requests. */
public final class RpcSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /** Creates the RPC span name extractor using the supplied instance's configuration. */
  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      RpcAttributesGetter<REQUEST, ?> getter, OpenTelemetry openTelemetry) {
    return new RpcSpanNameExtractor<>(getter, openTelemetry);
  }

  private final RpcAttributesGetter<REQUEST, ?> getter;
  private final boolean emitPreviewRpcSemconv;

  private RpcSpanNameExtractor(
      RpcAttributesGetter<REQUEST, ?> getter, OpenTelemetry openTelemetry) {
    this.getter = getter;
    this.emitPreviewRpcSemconv = emitPreviewRpcSemconv(openTelemetry);
  }

  @SuppressWarnings("deprecation") // for getMethod()
  @Override
  public String extract(REQUEST request) {
    if (emitPreviewRpcSemconv) {
      String method = getter.getRpcMethod(request);
      if (method != null) {
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
