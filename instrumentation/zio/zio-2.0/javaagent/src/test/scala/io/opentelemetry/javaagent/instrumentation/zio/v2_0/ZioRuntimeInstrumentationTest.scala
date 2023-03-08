/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0

import io.opentelemetry.instrumentation.testing.junit._
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.groupTraces
import io.opentelemetry.javaagent.instrumentation.zio.v2_0.ZioTestFixtures._
import io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}

import java.util
import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZioRuntimeInstrumentationTest {

  @RegisterExtension
  val testing: InstrumentationExtension =
    RelaxedInstrumentationExtension.create()

  @Test
  def traceIsPropagatedToChildFiber(): Unit = {
    runNestedFibers()

    assertThat(traces).hasTracesSatisfyingExactly(
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

    assertThat(traces).hasTracesSatisfyingExactly(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def concurrentFibersDoNotInterfereWithEachOthersTraces(): Unit = {
    runConcurrentFibers()

    assertThat(traces).hasTracesSatisfyingExactly(
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

  private def traces: util.List[util.List[SpanData]] =
    groupTraces(testing.spans())

  private def assertTrace(f: TraceAssert => Any): Consumer[TraceAssert] =
    (t: TraceAssert) => f(t)

  private def assertSpan(f: SpanDataAssert => Any): Consumer[SpanDataAssert] =
    (t: SpanDataAssert) => f(t)

}
