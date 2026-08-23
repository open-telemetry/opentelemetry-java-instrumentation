/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinx.coroutines.flow.v1_3

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalCoroutinesApi
class FlowWithSpanTest {

  @RegisterExtension
  val testing = AgentInstrumentationExtension.create()

  @Test
  fun `test method returning Flow with WithSpan annotation`() {
    var flowStartTime: Long = 0
    runBlocking {
      val flow = simple()
      val now = Clock.systemUTC().instant()
      flowStartTime = TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano
      flow.count()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("FlowWithSpanTest.simple")
              .hasNoParent()
              .hasAttributesSatisfyingExactly(
                SemconvCodeStabilityUtil.codeFunctionAssertions(this.javaClass, "simple")
              )
              .satisfies(Consumer { spanData ->
                assertThat(spanData.endEpochNanos).isGreaterThan(flowStartTime)
              })
          }
        )
      }
    )
  }

  @WithSpan
  fun simple(): Flow<Int> = flow {
    for (i in 1..3) {
      delay(100)
      emit(i)
    }
  }
}
