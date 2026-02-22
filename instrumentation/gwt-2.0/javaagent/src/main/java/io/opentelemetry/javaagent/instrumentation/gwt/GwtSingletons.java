/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import java.lang.reflect.Method;

public final class GwtSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.gwt-2.0";

  public static final ContextKey<Boolean> RPC_CONTEXT_KEY =
      ContextKey.named("opentelemetry-gwt-rpc-context-key");

  public static final ContextKey<Throwable[]> RPC_THROWABLE_KEY =
      ContextKey.named("opentelemetry-gwt-rpc-throwable");

  private static final Instrumenter<Method, Void> INSTRUMENTER;

  static {
    GwtRpcAttributesGetter rpcAttributesGetter = GwtRpcAttributesGetter.INSTANCE;
    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter));
    Experimental.setExceptionEventExtractor(builder, RpcExceptionEventExtractors.server());
    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GwtSingletons() {}
}
