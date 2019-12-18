package datadog.perftest;

import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;

import datadog.trace.api.Trace;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.concurrent.TimeUnit;

public class Worker {

  @Trace
  /** Simulate work for the give number of milliseconds. */
  public static void doWork(final long workTimeMS) {
    final AgentSpan span = activeSpan();
    if (span != null) {
      span.setTag("work-time", workTimeMS);
      span.setTag("info", "interesting stuff");
      span.setTag("additionalInfo", "interesting stuff");
    }

    final long doneTimestamp = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(workTimeMS);
    while (System.nanoTime() < doneTimestamp) {
      // busy-wait to simulate work
    }
  }
}
