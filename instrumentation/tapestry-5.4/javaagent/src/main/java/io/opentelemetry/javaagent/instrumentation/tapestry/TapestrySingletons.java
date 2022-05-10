/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.tapestry5.runtime.ComponentEventException;

public class TapestrySingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tapestry-5.4";

  private static final Instrumenter<TapestryRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<TapestryRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, TapestryRequest::spanName)
            .setErrorCauseExtractor(
                error -> {
                  if (error instanceof ComponentEventException) {
                    error = error.getCause();
                  }
                  return ErrorCauseExtractor.jdk().extract(error);
                })
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .newInstrumenter();
  }

  public static Instrumenter<TapestryRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private TapestrySingletons() {}
}
