/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.Method;

public class GwtSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.gwt-2.0";

  public static final ContextKey<Boolean> RPC_CONTEXT_KEY =
      ContextKey.named("opentelemetry-gwt-rpc-context-key");

  private static final Instrumenter<Method, Void> instrumenter;

  static {
    GwtRpcAttributesGetter rpcAttributesGetter = new GwtRpcAttributesGetter();
    instrumenter =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return instrumenter;
  }

  private GwtSingletons() {}
}
