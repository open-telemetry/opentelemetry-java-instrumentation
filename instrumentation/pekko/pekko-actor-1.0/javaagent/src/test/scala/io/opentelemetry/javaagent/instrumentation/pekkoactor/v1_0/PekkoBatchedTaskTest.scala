/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.trace.data.SpanData
import org.apache.pekko.actor.ActorSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier

import java.util.function.Consumer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/** Since scala 2.13 the task that runs the body of a Future is batchable, and a
  * pekko dispatcher appends such a task to a batch that is already running on
  * the current thread rather than submitting it to the pool. A future that is
  * created inside an already running task therefore needs a context of its own,
  * the one captured when the enclosing batch was submitted predates the span
  * that is current when the future is created.
  */
class PekkoBatchedTaskTest {

  @RegisterExtension val testing: InstrumentationExtension =
    AgentInstrumentationExtension.create()

  @Test def futureCreatedInsideRunningTaskKeepsContext(): Unit = {
    val system = ActorSystem("batched-task-test")
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      // the outer future is the task that the batch is created for, the inner one is appended to
      // that batch and only runs once the outer task has returned
      val outer = Future {
        testing.runWithSpan(
          "parent",
          new ThrowingSupplier[Future[Unit], RuntimeException] {
            override def get(): Future[Unit] =
              Future {
                testing.runWithSpan(
                  "child",
                  new ThrowingSupplier[Unit, RuntimeException] {
                    override def get(): Unit = ()
                  }
                )
              }
          }
        )
      }

      Await.result(Await.result(outer, 10.seconds), 10.seconds)

      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent()
                ()
              }
            },
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span
                  .hasName("child")
                  .hasKind(SpanKind.INTERNAL)
                  .hasParent(trace.getSpan(0).asInstanceOf[SpanData])
                ()
              }
            }
          )
      })
    } finally Await.result(system.terminate(), 10.seconds)
  }
}
