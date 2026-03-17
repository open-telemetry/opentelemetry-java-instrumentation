/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0

import io.opentelemetry.instrumentation.testing.junit._
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable
import io.opentelemetry.javaagent.instrumentation.zio.v2_0.ZioTestFixtures._
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}
import zio.{Trace, Unsafe, ZIO}

import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZioRuntimeInstrumentationTest {

  @RegisterExtension
  val testing: InstrumentationExtension = AgentInstrumentationExtension.create()

  @Test
  def traceIsPropagatedToChildFiber(): Unit = {
    runNestedFibers()

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
    runInterruptedFiber()

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
    runSynchronizedFibers()

    testing.waitAndAssertTraces(
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
    runConcurrentFibers()

    testing.waitAndAssertSortedTraces(
      orderByRootSpanName("fiber_1_span_1", "fiber_2_span_1", "fiber_3_span_1"),
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
    runSequentialFibers()

    testing.waitAndAssertTraces(
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

  def withSpan(name: String, fun: Unit => Unit): Unit = {
    testing.runWithSpan(
      name,
      new ThrowingRunnable[Exception] {
        override def run(): Unit = {
          fun.apply()
        }
      }
    )
  }

  @Test
  def unsafeRunShouldNotDestroyCallerThreadContext(): Unit = {
    withSpan(
      "parent",
      _ => {
        withSpan("before", _ => ())
        Unsafe.unsafe { implicit unsafe =>
          zio.Runtime.default.unsafe
            .run(ZIO.succeed("hello"))(Trace.empty, unsafe)
            .getOrThrowFiberFailure()
        }
        withSpan("after", _ => ())
      }
    )

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("parent").hasNoParent),
          assertSpan(_.hasName("before").hasParent(trace.getSpan(0))),
          assertSpan(_.hasName("after").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  private def assertTrace(f: TraceAssert => Any): Consumer[TraceAssert] =
    (t: TraceAssert) => f(t)

  private def assertSpan(f: SpanDataAssert => Any): Consumer[SpanDataAssert] =
    (t: SpanDataAssert) => f(t)

}
