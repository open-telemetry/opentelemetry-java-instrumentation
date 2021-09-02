/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;

public final class HystrixSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hystrix-1.4";

  private static final Instrumenter<HystrixRequest, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<HystrixRequest, Void> builder =
        Instrumenter.newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, HystrixRequest::spanName);

    if (Config.get()
        .getBoolean("otel.instrumentation.hystrix.experimental-span-attributes", false)) {
      builder.addAttributesExtractor(new ExperimentalAttributesExtractor());
    }

    INSTRUMENTER = builder.newInstrumenter();
  }

  public static Instrumenter<HystrixRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private HystrixSingletons() {}
}
