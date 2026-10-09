/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;

/**
 * Provides {@link ContextCustomizer} instances for RPC metrics dual-semconv support.
 *
 * @deprecated This class is only needed during the transition period when both old and stable RPC
 *     semantic conventions are emitted simultaneously.
 */
@Deprecated // to be removed in 3.0
public final class RpcMetricsContextCustomizers {

  static final ContextKey<String> OLD_RPC_METHOD_CONTEXT_KEY =
      ContextKey.named("otel-rpc-old-method");

  /** Creates the dual-emission customizer using the supplied instance's configuration. */
  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static <REQUEST> ContextCustomizer<REQUEST> dualEmitContextCustomizer(
      RpcAttributesGetter<REQUEST, ?> getter, OpenTelemetry openTelemetry) {
    boolean dualEmit = emitOldRpcSemconv(openTelemetry) && emitPreviewRpcSemconv(openTelemetry);
    return (context, request, startAttributes) -> {
      if (dualEmit) {
        String oldMethod = getter.getMethod(request);
        if (oldMethod != null) {
          return context.with(OLD_RPC_METHOD_CONTEXT_KEY, oldMethod);
        }
      }
      return context;
    };
  }

  private RpcMetricsContextCustomizers() {}
}
