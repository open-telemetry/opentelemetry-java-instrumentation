/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class MetroSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-2.0-metro-2.2";

  private static final Instrumenter<MetroRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<MetroRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, MetroRequest::spanName)
            .setDisabled(ExperimentalConfig.get().suppressControllerSpans())
            .newInstrumenter();
  }

  public static Instrumenter<MetroRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private MetroSingletons() {}
}
