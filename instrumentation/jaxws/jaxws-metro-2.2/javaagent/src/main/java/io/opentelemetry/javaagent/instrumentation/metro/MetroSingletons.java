/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public class MetroSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-metro-2.2";

  private static final Instrumenter<MetroRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<MetroRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, MetroRequest::spanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  public static Instrumenter<MetroRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private MetroSingletons() {}
}
