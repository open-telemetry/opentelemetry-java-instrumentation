package controllers

import datadog.trace.api.Trace
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import java.util.concurrent.TimeUnit

object Worker {
  @Trace
  def doWork(workTimeMS: Long) = {
    val span = GlobalTracer.get.activeSpan
    if (span != null) {
      span.setTag("work-time", workTimeMS)
      span.setTag("info", "interesting stuff")
      span.setTag("additionalInfo", "interesting stuff")
    }
    val doneTimestamp = System.nanoTime + TimeUnit.MILLISECONDS.toNanos(workTimeMS)
    while ( {
      System.nanoTime < doneTimestamp
    }) {
      // busy-wait to simulate work
    }
  }
}
