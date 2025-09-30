/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.Method;

public final class RmiClientSingletons {

  private static final Instrumenter<Method, Void> INSTRUMENTER;

  static {
    RmiClientAttributesGetter rpcAttributesGetter = RmiClientAttributesGetter.INSTANCE;

    INSTRUMENTER =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RmiClientSingletons() {}
}
