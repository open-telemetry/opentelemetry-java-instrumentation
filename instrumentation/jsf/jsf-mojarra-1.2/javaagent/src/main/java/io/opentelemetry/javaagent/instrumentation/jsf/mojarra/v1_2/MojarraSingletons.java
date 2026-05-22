/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.mojarra.v1_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jsf.common.javax.JsfErrorCauseExtractor;
import io.opentelemetry.javaagent.instrumentation.jsf.common.javax.JsfRequest;

class MojarraSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jsf-mojarra-1.2";

  private static final Instrumenter<JsfRequest, Void> instrumenter;

  static {
    instrumenter =
        Instrumenter.<JsfRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, JsfRequest::getSpanName)
            .setErrorCauseExtractor(new JsfErrorCauseExtractor())
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  static Instrumenter<JsfRequest, Void> instrumenter() {
    return instrumenter;
  }

  private MojarraSingletons() {}
}
