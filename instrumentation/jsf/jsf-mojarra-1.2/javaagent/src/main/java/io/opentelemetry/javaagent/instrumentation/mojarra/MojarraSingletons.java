/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mojarra;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jsf.javax.JsfErrorCauseExtractor;
import io.opentelemetry.javaagent.instrumentation.jsf.javax.JsfRequest;

public class MojarraSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jsf-mojarra-1.2";

  private static final Instrumenter<JsfRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<JsfRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, JsfRequest::spanName)
            .setErrorCauseExtractor(new JsfErrorCauseExtractor())
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  public static Instrumenter<JsfRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private MojarraSingletons() {}
}
