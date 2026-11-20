/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry.v5_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.apache.tapestry5.runtime.ComponentEventException;

class TapestrySingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tapestry-5.4";

  private static final Instrumenter<TapestryRequest, Void> instrumenter;

  static {
    instrumenter =
        Instrumenter.<TapestryRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, TapestryRequest::spanName)
            .setErrorCauseExtractor(
                error -> {
                  if (error instanceof ComponentEventException) {
                    error = error.getCause();
                  }
                  return ErrorCauseExtractor.getDefault().extract(error);
                })
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  static Instrumenter<TapestryRequest, Void> instrumenter() {
    return instrumenter;
  }

  private TapestrySingletons() {}
}
