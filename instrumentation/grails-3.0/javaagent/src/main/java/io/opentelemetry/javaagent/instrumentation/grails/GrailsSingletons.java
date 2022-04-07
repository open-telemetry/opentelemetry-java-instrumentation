/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class GrailsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grails-3.0";

  private static final Instrumenter<HandlerData, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<HandlerData, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, HandlerData::spanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .newInstrumenter();
  }

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GrailsSingletons() {}
}
