/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;

public class ResponseSingletons {

  private static final Instrumenter<Method, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<Method, Void>builder(
                GlobalOpenTelemetry.get(), "io.opentelemetry.servlet-5.0", SpanNames::fromMethod)
            .newInstrumenter();
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }
}
