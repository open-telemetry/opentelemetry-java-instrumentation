/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import java.lang.reflect.Method;

public final class GwtSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.gwt-2.0";

  public static final ContextKey<Boolean> RPC_CONTEXT_KEY =
      ContextKey.named("opentelemetry-gwt-rpc-context-key");

  private static final Instrumenter<Method, Void> INSTRUMENTER;

  static {
    GwtRpcAttributesGetter rpcAttributesGetter = GwtRpcAttributesGetter.INSTANCE;
    INSTRUMENTER =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            // TODO(anuraaga): This should be a server span, but we currently have no way to merge
            // with the HTTP instrumentation's server span.
            .newInstrumenter();
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GwtSingletons() {}
}
