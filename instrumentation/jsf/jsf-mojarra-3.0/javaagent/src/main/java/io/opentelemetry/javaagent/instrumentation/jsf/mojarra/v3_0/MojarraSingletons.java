/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.mojarra.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jsf.common.jakarta.JsfErrorCauseExtractor;
import io.opentelemetry.javaagent.instrumentation.jsf.common.jakarta.JsfRequest;

public class MojarraSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jsf-mojarra-3.0";

  private static final Instrumenter<JsfRequest, Void> instrumenter;

  static {
    instrumenter =
        Instrumenter.<JsfRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, JsfRequest::getSpanName)
            .setErrorCauseExtractor(new JsfErrorCauseExtractor())
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  public static Instrumenter<JsfRequest, Void> instrumenter() {
    return instrumenter;
  }

  private MojarraSingletons() {}
}
