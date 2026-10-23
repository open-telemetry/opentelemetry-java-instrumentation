/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSemconvSelection;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.internal.OpenTelemetryConfigurable;

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
    return new DualEmitContextCustomizer<>(getter);
  }

  private RpcMetricsContextCustomizers() {}

  private static final class DualEmitContextCustomizer<REQUEST>
      implements ContextCustomizer<REQUEST>, OpenTelemetryConfigurable {
    private final RpcAttributesGetter<REQUEST, ?> getter;
    private final RpcSemconvSelection selection = new RpcSemconvSelection();

    private DualEmitContextCustomizer(RpcAttributesGetter<REQUEST, ?> getter) {
      this.getter = getter;
    }

    @Override
    public void internalInitialize(OpenTelemetry openTelemetry) {
      selection.initialize(openTelemetry);
    }

    @SuppressWarnings("deprecation") // for getMethod()
    @Override
    public Context onStart(Context context, REQUEST request, Attributes startAttributes) {
      RpcSemconvSelection.Selection current = selection.get();
      if (current.emitOld() && current.emitPreview()) {
        String oldMethod = getter.getMethod(request);
        if (oldMethod != null) {
          return context.with(OLD_RPC_METHOD_CONTEXT_KEY, oldMethod);
        }
      }
      return context;
    }
  }
}
