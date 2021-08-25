/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.spring.webflux.SpringWebfluxConfig;

public final class WebfluxSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.0";

  private static final Instrumenter<Object, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<Object, Void> builder =
        Instrumenter.newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, new WebfluxSpanNameExtractor());

    if (SpringWebfluxConfig.captureExperimentalSpanAttributes()) {
      builder.addAttributesExtractor(new ExperimentalAttributesExtractor());
    }

    INSTRUMENTER =
        builder.setDisabled(ExperimentalConfig.get().suppressControllerSpans()).newInstrumenter();
  }

  public static Instrumenter<Object, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private WebfluxSingletons() {}
}
