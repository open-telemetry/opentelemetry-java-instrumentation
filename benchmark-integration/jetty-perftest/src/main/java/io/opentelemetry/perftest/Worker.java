package io.opentelemetry.perftest;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.TimeUnit;

public class Worker {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  /** Simulate work for the give number of milliseconds. */
  public static void doWork(final long workTimeMS) {
    final Span span = TRACER.spanBuilder("work").startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      if (span != null) {
        span.setAttribute("work-time", workTimeMS);
        span.setAttribute("info", "interesting stuff");
        span.setAttribute("additionalInfo", "interesting stuff");
      }

      final long doneTimestamp = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workTimeMS);
      while (System.nanoTime() < doneTimestamp) {
        // busy-wait to simulate work
      }
      span.end();
    }
  }
}
