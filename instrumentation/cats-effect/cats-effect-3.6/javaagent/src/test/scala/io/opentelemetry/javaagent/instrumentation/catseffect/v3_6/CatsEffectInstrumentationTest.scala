/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6

import java.util.concurrent.Executors

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import io.opentelemetry.instrumentation.testing.junit._
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}
import java.util.function.Consumer

import cats.effect.std.Dispatcher
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatsEffectInstrumentationTest {

  @RegisterExtension
  val testing: InstrumentationExtension = AgentInstrumentationExtension.create()

  @Test
  def respectOuterSpanWithUnsafeRunSync(): Unit = {
    testing.runWithSpan[Exception](
      "main_1_span_1",
      () => {
        withIOSpan("fiber_1_span_1")(_ => IO.unit).unsafeRunSync()
      }
    )

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("main_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def respectOuterSpanAndPropagateToLiftedFuture(): Unit = {
    testing.runWithSpan[Exception](
      "main_1_span_1",
      () => {
        withIOSpan("fiber_1_span_1") { _ =>
          IO.fromFuture(IO.delay {
            Future(testing.runWithSpan[Exception]("future_1_span_1", () => ()))(
              ExecutionContext.global
            )
          })
        }.unsafeRunSync()
      }
    )

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("main_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_1").hasParent(trace.getSpan(0))),
          assertSpan(_.hasName("future_1_span_1").hasParent(trace.getSpan(1)))
        )
      }
    )
  }

  @Test
  def respectOuterSpanWithUnsafeRunToFuture(): Unit = {
    testing.runWithSpan[Exception](
      "main_1_span_1",
      () => {
        Await.result(
          withIOSpan("fiber_1_span_1")(_ => IO.unit).unsafeToFuture(),
          Duration.Inf
        )
      }
    )

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("main_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def respectOuterSpanWithDispatcher(): Unit = {
    testing.runWithSpan[Exception](
      "main_1_span_1",
      () => {
        withIOSpan("fiber_1_span_1") { _ =>
          Dispatcher.sequential[IO].use { dispatcher =>
            IO.blocking {
              dispatcher.unsafeRunSync(
                withIOSpan("dispatcher_1_span_1")(_ => IO.unit)
              )
            }
          }
        }.unsafeRunSync()
      }
    )

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("main_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_1").hasParent(trace.getSpan(0))),
          assertSpan(
            _.hasName("dispatcher_1_span_1").hasParent(trace.getSpan(1))
          )
        )
      }
    )
  }

  @Test
  def traceIsPropagatedToChildFiber(): Unit = {
    withIOSpan("fiber_1_span_1") { _ =>
      for {
        child <- withIOSpan("fiber_2_span_1")(_ => IO.unit).start
        _ <- child.join
      } yield ()
    }.unsafeRunSync()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def traceIsPropagatedToChildFiberOnExternalExecutor(): Unit = {
    withIOSpan("fiber_1_span_1") { _ =>
      for {
        child <- withIOSpan("fiber_2_span_1")(_ => IO.unit).startOnExecutor(
          Executors.newSingleThreadExecutor()
        )
        _ <- child.join
      } yield ()
    }.unsafeRunSync()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def traceIsPreservedWhenFiberIsInterrupted(): Unit = {
    (for {
      childStarted <- IO.deferred[Unit]
      _ <- withIOSpan("fiber_1_span_1") { _ =>
        for {
          child <- IO.defer(
            withIOSpan("fiber_2_span_1")(_ =>
              (childStarted.complete(()) *> IO.never[Unit]).start
            )
          )
          _ <- childStarted.get
          _ <- child.cancel
        } yield ()
      }
    } yield ()).unsafeRunSync()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def synchronizedFibersDoNotInterfereWithEachOthersTraces(): Unit = {

    def runFiber(
        fiberNumber: Int,
        onStart: IO[Unit],
        onEnd: IO[Unit]
    ): IO[Unit] =
      withIOSpan(s"fiber_${fiberNumber}_span_1") { _ =>
        onStart *> withIOSpan(s"fiber_${fiberNumber}_span_2")(_ => onEnd)
      }

    (for {
      fiber1Started <- IO.deferred[Unit]
      fiber2Done <- IO.deferred[Unit]

      fiber1 <- runFiber(
        fiberNumber = 1,
        onStart = fiber1Started.complete(()) *> fiber2Done.get,
        onEnd = IO.unit
      ).start

      fiber2 <- runFiber(
        fiberNumber = 2,
        onStart = fiber1Started.get,
        onEnd = fiber2Done.complete(()).void
      ).start

      _ <- fiber1.join *> fiber2.join
    } yield ()).unsafeRunSync()

    testing.waitAndAssertSortedTraces(
      TelemetryDataUtil.orderByRootSpanName(
        "fiber_1_span_1",
        "fiber_1_span_2",
        "fiber_2_span_1",
        "fiber_2_span_2"
      ),
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_2_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_2").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def concurrentFibersDoNotInterfereWithEachOthersTraces(): Unit = {

    def runFiber(
        fiberNumber: Int,
        start: Deferred[IO, Unit]
    ): IO[Unit] = {
      start.get *>
        withIOSpan(s"fiber_${fiberNumber}_span_1") { _ =>
          IO.cede *> withIOSpan(s"fiber_${fiberNumber}_span_2")(_ => IO.unit)
        }
    }

    (for {
      start <- IO.deferred[Unit]
      fiber1 <- runFiber(1, start).start
      fiber2 <- runFiber(2, start).start
      fiber3 <- runFiber(3, start).start
      _ <- start.complete(())
      _ <- fiber1.join *> fiber2.join *> fiber3.join
    } yield ()).unsafeRunSync()

    testing.waitAndAssertSortedTraces(
      TelemetryDataUtil.orderByRootSpanName(
        "fiber_1_span_1",
        "fiber_2_span_1",
        "fiber_3_span_1"
      ),
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_2_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_3_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_3_span_2").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def sequentialFibersDoNotInterfereWithEachOthersTraces(): Unit = {

    def runFiber(fiberNumber: Int): IO[Unit] =
      withIOSpan(s"fiber_${fiberNumber}_span_1") { _ =>
        withIOSpan(s"fiber_${fiberNumber}_span_2")(_ => IO.unit)
      }

    (for {
      fiber1 <- runFiber(1).start
      _ <- fiber1.join
      fiber2 <- runFiber(2).start
      _ <- fiber2.join
      fiber3 <- runFiber(3).start
      _ <- fiber3.join
    } yield ()).unsafeRunSync()

    testing.waitAndAssertSortedTraces(
      TelemetryDataUtil.orderByRootSpanName(
        "fiber_1_span_1",
        "fiber_1_span_2",
        "fiber_2_span_1",
        "fiber_2_span_2",
        "fiber_3_span_1",
        "fiber_3_span_2"
      ),
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_2_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_3_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_3_span_2").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  private def assertTrace(f: TraceAssert => Any): Consumer[TraceAssert] =
    (t: TraceAssert) => f(t)

  private def assertSpan(f: SpanDataAssert => Any): Consumer[SpanDataAssert] =
    (t: SpanDataAssert) => f(t)

  private val tracer = GlobalOpenTelemetry.getTracer("test")

  private def withIOSpan[A](name: String)(f: Span => IO[A]): IO[A] =
    IO.delay {
      val span = tracer.spanBuilder(name).startSpan()
      val scope = span.makeCurrent()
      (span, scope)
    }.bracket { case (span, _) => f(span) } { case (span, scope) =>
      IO.delay {
        scope.close()
        span.end()
      }
    }

}
