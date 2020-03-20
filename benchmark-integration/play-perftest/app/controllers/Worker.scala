package controllers

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer
import java.util.concurrent.TimeUnit

object Worker {
  val TRACER: Tracer = OpenTelemetry.getTracerProvider.get("io.opentelemetry.auto")

  def doWork(workTimeMS: Long) = {
    val span = TRACER.spanBuilder("work").startSpan()
    val scope = TRACER.withSpan(span)
    try {
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
    } finally {
      span.end()
      scope.close()
    }
  }
}
