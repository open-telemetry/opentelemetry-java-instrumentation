/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.after
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.function.Consumer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

class PekkoSchedulerTest {
  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create()

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
          tracer.spanBuilder("future").startSpan().end()
          1
        }
        _ = tracer.spanBuilder("first continuation").startSpan().end()
        result2 <- after(200.millis, system.scheduler)(Future.successful(2))
        _ = tracer.spanBuilder("second continuation").startSpan().end()
      } yield result1 + result2
      assertThat(Await.result(futureResult, 5.seconds)).isEqualTo(3)
    } finally {
      scope.close()
      initialSpan.end()
      Await.result(system.terminate(), 5.seconds)
    }
    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit = {
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName("test").hasNoParent()
              ()
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName("future").hasParent(trace.getSpan(0))
              ()
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName("first continuation").hasParent(trace.getSpan(0))
              ()
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName("second continuation").hasParent(trace.getSpan(0))
              ()
            }
          }
        )
        ()
      }
    })
  }
}
