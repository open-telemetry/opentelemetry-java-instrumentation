/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.myfaces.v1_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jsf.common.javax.JsfRequest;

final class MyFacesSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jsf-myfaces-1.2";

  private static final Instrumenter<JsfRequest, Void> instrumenter;

  static {
    instrumenter =
        Instrumenter.<JsfRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, JsfRequest::getSpanName)
            .setErrorCauseExtractor(new MyFacesErrorCauseExtractor())
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();
  }

  public static Instrumenter<JsfRequest, Void> instrumenter() {
    return instrumenter;
  }

  private MyFacesSingletons() {}
}
