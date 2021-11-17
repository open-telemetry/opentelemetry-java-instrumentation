/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import java.lang.reflect.Method;

public final class RmiClientSingletons {

  private static final Instrumenter<Method, Void> INSTRUMENTER;

  static {
    RmiClientAttributesExtractor attributesExtractor = new RmiClientAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.rmi",
                RpcSpanNameExtractor.create(attributesExtractor))
            .addAttributesExtractor(attributesExtractor)
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RmiClientSingletons() {}
}
