/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNames;
import java.lang.reflect.Method;

public final class SpringDataSingletons {

  private static final Instrumenter<Method, Void> INSTRUMENTER =
      Instrumenter.<Method, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.spring-data-1.8", SpanNames::fromMethod)
          .newInstrumenter();

  public static Instrumenter<Method, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringDataSingletons() {}
}
