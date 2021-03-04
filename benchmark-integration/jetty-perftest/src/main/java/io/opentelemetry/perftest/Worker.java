/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.perftest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.concurrent.TimeUnit;

public class Worker {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  /** Simulate work for the give number of milliseconds. */
  public static void doWork(long workTimeMillis) {
    Span span = tracer.spanBuilder("work").startSpan();
    try (Scope scope = span.makeCurrent()) {
      if (span != null) {
        span.setAttribute("work-time", workTimeMillis);
        span.setAttribute("info", "interesting stuff");
        span.setAttribute("additionalInfo", "interesting stuff");
      }

      long doneTimestamp = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workTimeMillis);
      while (System.nanoTime() < doneTimestamp) {
        // busy-wait to simulate work
      }
      span.end();
    }
  }
}
