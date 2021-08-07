/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;

public final class GrailsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grails-3.0";

  private static final Instrumenter<ControllerAction, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<ControllerAction, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, GrailsSingletons::spanName)
            .newInstrumenter();
  }

  public static Instrumenter<ControllerAction, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private static String spanName(ControllerAction controllerAction) {
    return SpanNames.fromMethod(
        controllerAction.getController().getClass(), controllerAction.getAction());
  }

  private GrailsSingletons() {}
}
