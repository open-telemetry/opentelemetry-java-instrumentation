package io.opentelemetry.perftest;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;

import io.opentelemetry.auto.api.Trace;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.util.concurrent.TimeUnit;

public class Worker {

  @Trace
  /** Simulate work for the give number of milliseconds. */
  public static void doWork(final long workTimeMS) {
    final AgentSpan span = activeSpan();
    if (span != null) {
      span.setAttribute("work-time", workTimeMS);
      span.setAttribute("info", "interesting stuff");
      span.setAttribute("additionalInfo", "interesting stuff");
    }

    final long doneTimestamp = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workTimeMS);
    while (System.nanoTime() < doneTimestamp) {
      // busy-wait to simulate work
    }
  }
}
