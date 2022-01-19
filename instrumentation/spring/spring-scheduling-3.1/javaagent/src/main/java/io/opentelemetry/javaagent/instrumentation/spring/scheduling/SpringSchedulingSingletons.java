/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNames;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

public final class SpringSchedulingSingletons {

  private static final Instrumenter<Runnable, Void> INSTRUMENTER =
      Instrumenter.<Runnable, Void>builder(
              GlobalOpenTelemetry.get(),
              "io.opentelemetry.spring-scheduling-3.1",
              SpringSchedulingSingletons::extractSpanName)
          .newInstrumenter();

  private static String extractSpanName(Runnable runnable) {
    if (runnable instanceof ScheduledMethodRunnable) {
      ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) runnable;
      return SpanNames.fromMethod(scheduledMethodRunnable.getMethod());
    } else {
      return SpanNames.fromMethod(runnable.getClass(), "run");
    }
  }

  public static Instrumenter<Runnable, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SpringSchedulingSingletons() {}
}
