/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

class PekkoSchedulerTest {

  @Test
  def checkThatSpanWorksWithPekkoScheduledEvents(): Unit = {
    val system = ActorSystem("my-system")
    implicit val executionContext = system.dispatcher
    val tracer = GlobalOpenTelemetry.get.getTracer("test-tracer")
    val initialSpan = tracer.spanBuilder("test").startSpan()
    val scope = initialSpan.makeCurrent()
    try {
      val futureResult = for {
        result1 <- Future {
          compareSpanContexts(Span.current(), initialSpan)
          1
        }
        _ = compareSpanContexts(Span.current(), initialSpan)
        result2 <- after(200.millis, system.scheduler)(Future.successful(2))
        _ = compareSpanContexts(Span.current(), initialSpan)
      } yield result1 + result2
      assertThat(Await.result(futureResult, 5.seconds)).isEqualTo(3)
    } finally {
      system.terminate()
      scope.close()
      initialSpan.end()
    }
  }

  private def compareSpanContexts(span1: Span, span2: Span): Unit = {
    assertThat(span1.getSpanContext().getTraceId())
      .isEqualTo(span2.getSpanContext().getTraceId())
    assertThat(span1.getSpanContext().getSpanId())
      .isEqualTo(span2.getSpanContext().getSpanId())
  }
}
