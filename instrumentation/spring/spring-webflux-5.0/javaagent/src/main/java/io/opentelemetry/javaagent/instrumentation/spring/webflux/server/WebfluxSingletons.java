/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class WebfluxSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.0";

  private static final Instrumenter<Void, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<Void, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, v -> "DispatcherHandler.handle")
            .newInstrumenter();
  }

  public static Instrumenter<Void, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private WebfluxSingletons() {}
}
