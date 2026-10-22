/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.instrumentation.testing.junit.{
  AgentInstrumentationExtension,
  InstrumentationExtension
}
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}

import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultEnablementTest {

  @RegisterExtension
  val testing: InstrumentationExtension = AgentInstrumentationExtension.create()

  @Test
  def defaultEnablement(): Unit = {
    val parent = testing.getOpenTelemetry
      .getTracer("test")
      .spanBuilder("parent")
      .startSpan()
    val scope = parent.makeCurrent()
    try {
      val propagated = ZioTestFixtures.currentSpanAfterSuspension()
      val expected =
        if (java.lang.Boolean.getBoolean("otel.instrumentation.zio.enabled"))
          parent.getSpanContext
        else SpanContext.getInvalid
      assertThat(propagated).isEqualTo(expected)
    } finally {
      scope.close()
      parent.end()
    }

    testing.waitAndAssertTraces(
      new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(
            new Consumer[SpanDataAssert] {
              override def accept(span: SpanDataAssert): Unit = {
                span.hasName("parent")
              }
            }
          )
      }
    )
  }
}
