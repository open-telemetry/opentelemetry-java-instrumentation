/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.javaagent.testing.common.Java8BytecodeBridge
import io.opentelemetry.sdk.testing.assertj.{
  OpenTelemetryAssertions,
  SpanDataAssert,
  TraceAssert
}
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.function.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScalaInstrumentationTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create()

  val tracer: Tracer = testing.getOpenTelemetry().getTracer("test")

  @Test
  def scalaFuturesAndCallbacks(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      val goodFuture = Future {
        tracedChild("goodFuture")
        1
      }
      goodFuture.onSuccess { case _ => tracedChild("successCallback") }
      assertThat(Await.result(goodFuture, 10.seconds)).isEqualTo(1)
      val badFuture = Future {
        tracedChild("badFuture")
        throw new IllegalStateException("Uh-oh")
      }
      badFuture.onFailure { case _ => tracedChild("failureCallback") }
      assertThatThrownBy(new ThrowingCallable {
        override def call(): Unit = Await.result(badFuture, 10.seconds)
      }).isInstanceOf(classOf[IllegalStateException])
    } finally {
      parentScope.close()
      parentSpan.end()
    }

    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("parent")
                  .hasNoParent()
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("goodFuture")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("successCallback")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("badFuture")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("failureCallback")
                  .hasParent(trace.getSpan(0))
            }
          )
      }
    )
  }

  @Test
  def propagatesAcrossFuturesWithNoTraces(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      val goodFuture = Future { 1 }
      goodFuture.onSuccess { case _ =>
        Future { 2 }.onSuccess { case _ =>
          tracedChild("callback")
        }
      }
    } finally {
      parentSpan.end()
      parentScope.close()
    }

    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("parent")
                  .hasNoParent()
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("callback")
                  .hasParent(trace.getSpan(0))
            }
          )
      }
    )
  }

  @Test
  def eitherPromiseCompletion(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      val keptPromise = Promise[Boolean]()
      val brokenPromise = Promise[Boolean]()
      val afterPromise = keptPromise.future
      val afterPromise2 = keptPromise.future

      val failedAfterPromise = brokenPromise.future

      Future {
        tracedChild("future1")
        keptPromise.success(true)
        brokenPromise.failure(new IllegalStateException())
      }

      afterPromise.onSuccess { case _ =>
        tracedChild("keptPromise")
      }
      Await.result(afterPromise, 10.seconds)
      afterPromise2.onSuccess { case _ =>
        tracedChild("keptPromise2")
      }
      Await.result(afterPromise2, 10.seconds)

      failedAfterPromise.onFailure { case _ =>
        tracedChild("brokenPromise")
      }
      assertThatThrownBy(new ThrowingCallable {
        override def call(): Unit = Await.result(failedAfterPromise, 10.seconds)
      }).isInstanceOf(classOf[IllegalStateException])
    } finally {
      parentSpan.end()
      parentScope.close()
    }

    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("parent")
                  .hasNoParent()
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("future1")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("keptPromise")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("keptPromise2")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit =
                span
                  .hasName("brokenPromise")
                  .hasParent(trace.getSpan(0))
            }
          )
      }
    )
  }

  @Test
  def firstCompletedFuture(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      val completedVal = Future.firstCompletedOf(
        List(
          Future {
            tracedChild("timeout1")
            false
          },
          Future {
            tracedChild("timeout2")
            false
          },
          Future {
            tracedChild("timeout3")
            true
          }
        )
      )
      Await.result(completedVal, 30.seconds)
    } finally {
      parentSpan.end()
      parentScope.close()
    }

    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.satisfiesExactlyInAnyOrder(
            new Consumer[SpanData] {
              override def accept(span: SpanData): Unit =
                OpenTelemetryAssertions
                  .assertThat(span)
                  .hasName("parent")
                  .hasNoParent()
            },
            new Consumer[SpanData] {
              override def accept(span: SpanData): Unit =
                OpenTelemetryAssertions
                  .assertThat(span)
                  .hasName("timeout1")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanData] {
              override def accept(span: SpanData): Unit =
                OpenTelemetryAssertions
                  .assertThat(span)
                  .hasName("timeout2")
                  .hasParent(trace.getSpan(0))
            },
            new Consumer[SpanData] {
              override def accept(span: SpanData): Unit =
                OpenTelemetryAssertions
                  .assertThat(span)
                  .hasName("timeout3")
                  .hasParent(trace.getSpan(0))
            }
          )
      }
    )
  }

  private def tracedChild(opName: String): Unit = {
    tracer.spanBuilder(opName).startSpan().end()
  }
}
