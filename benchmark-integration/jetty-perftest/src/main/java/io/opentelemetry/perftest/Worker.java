/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.perftest;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.TimeUnit;

public class Worker {

  private static final Tracer TRACER = OpenTelemetry.getGlobalTracer("io.opentelemetry.auto");

  /** Simulate work for the give number of milliseconds. */
  public static void doWork(long workTimeMS) {
    Span span = TRACER.spanBuilder("work").startSpan();
    try (Scope scope = span.makeCurrent()) {
      if (span != null) {
        span.setAttribute("work-time", workTimeMS);
        span.setAttribute("info", "interesting stuff");
        span.setAttribute("additionalInfo", "interesting stuff");
      }

      long doneTimestamp = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workTimeMS);
      while (System.nanoTime() < doneTimestamp) {
        // busy-wait to simulate work
      }
      span.end();
    }
  }
}
