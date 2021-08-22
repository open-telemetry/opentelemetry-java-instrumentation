/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class GrailsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grails-3.0";

  private static final boolean SUPPRESS_CONTROLLER_SPANS =
      Config.get()
          .getBoolean("otel.instrumentation.common.experimental.suppress-controller-spans", false);

  private static final Instrumenter<HandlerData, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<HandlerData, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, HandlerData::spanName)
            .setDisabled(SUPPRESS_CONTROLLER_SPANS)
            .newInstrumenter();
  }

  public static Instrumenter<HandlerData, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private GrailsSingletons() {}
}
