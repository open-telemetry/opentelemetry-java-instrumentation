/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import java.lang.reflect.Method;

public final class MethodInstrumenters {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.javaagent.external-annotations";

  private static final Instrumenter<Method, Void> INSTRUMENTER;

  static {
    SpanNameExtractor<Method> spanName = SpanNames::spanNameForMethod;

    INSTRUMENTER =
        Instrumenter.<Method, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .newInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private MethodInstrumenters() {}
}
