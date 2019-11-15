package datadog.perftest;

import datadog.trace.api.Trace;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.TimeUnit;

public class Worker {

  @Trace
  /** Simulate work for the give number of milliseconds. */
  public static void doWork(final long workTimeMS) {
    final Span span = GlobalTracer.get().activeSpan();
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
