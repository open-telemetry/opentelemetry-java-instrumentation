package controllers

import io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan

import io.opentelemetry.auto.api.Trace
import java.util.concurrent.TimeUnit

object Worker {
  @Trace
  def doWork(workTimeMS: Long) = {
    val span = activeSpan
    if (span != null) {
      span.setAttribute("work-time", workTimeMS)
      span.setAttribute("info", "interesting stuff")
      span.setAttribute("additionalInfo", "interesting stuff")
    }
    val doneTimestamp = System.nanoTime + TimeUnit.MILLISECONDS.toNanos(workTimeMS)
    while ( {
      System.nanoTime < doneTimestamp
    }) {
      // busy-wait to simulate work
    }
  }
}
