/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jsf.JsfRequest;

public class MyFacesSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.myfaces-1.2";

  private static final boolean SUPPRESS_CONTROLLER_SPANS =
      Config.get()
          .getBoolean("otel.instrumentation.common.experimental.suppress-controller-spans", false);

  private static final Instrumenter<JsfRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<JsfRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, JsfRequest::spanName)
            .setErrorCauseExtractor(new MyFacesErrorCauseExtractor())
            .setDisabled(SUPPRESS_CONTROLLER_SPANS)
            .newInstrumenter();
  }

  public static Instrumenter<JsfRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private MyFacesSingletons() {}
}
