/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;

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

  /**
   * Returns a {@link ContextCustomizer} that captures the old {@code rpc.method} value in context
   * so that RPC metrics can use it when both old and stable semantic conventions are active.
   */
  public static <REQUEST> ContextCustomizer<REQUEST> dualEmitContextCustomizer(
      RpcAttributesGetter<REQUEST, ?> getter) {
    return (context, request, startAttributes) -> {
      if (emitOldRpcSemconv() && emitStableRpcSemconv()) {
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
