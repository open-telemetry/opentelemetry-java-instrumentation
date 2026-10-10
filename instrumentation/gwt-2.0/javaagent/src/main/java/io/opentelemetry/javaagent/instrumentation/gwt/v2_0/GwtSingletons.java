/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt.v2_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class GwtSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.gwt-2.0";

  // holds the failure that GWT encodes into the rpc response instead of throwing it
  public static final ContextKey<AtomicReference<Throwable>> RPC_FAILURE_KEY =
      ContextKey.named("opentelemetry-gwt-rpc-failure-key");

  private static final Instrumenter<Method, Void> instrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    GwtRpcAttributesGetter rpcAttributesGetter = new GwtRpcAttributesGetter();
    InstrumenterBuilder<Method, Void> builder =
        Instrumenter.<Method, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                RpcSpanNameExtractor.create(rpcAttributesGetter, openTelemetry))
            .addAttributesExtractor(
                RpcServerAttributesExtractor.create(rpcAttributesGetter, openTelemetry));
    setRpcServerExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return instrumenter;
  }

  private GwtSingletons() {}
}
