/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DefaultEnablementTest {

  companion object {
    private val ANNOTATIONS_ENABLED =
      java.lang.Boolean.getBoolean("otel.instrumentation.kotlinx-coroutines-annotations.enabled")

    @JvmField
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  @Test
  fun defaultEnablement() {
    val tracer = testing.openTelemetry.getTracer("test")
    val parent = tracer.spanBuilder("parent").startSpan()
    val scope = parent.makeCurrent()
    try {
      runBlocking {
        annotatedOperation()
      }
    } finally {
      scope.close()
      parent.end()
    }

    if (ANNOTATIONS_ENABLED) {
      testing.waitAndAssertTraces(
        { trace ->
          trace.hasSpansSatisfyingExactly(
            { it.hasName("parent") },
            { it.hasName("annotated").hasParent(trace.getSpan(0)) },
          )
        },
      )
    } else {
      testing.waitAndAssertTraces(
        { trace -> trace.hasSpansSatisfyingExactly({ it.hasName("parent") }) },
      )
    }
  }

  @WithSpan("annotated")
  private suspend fun annotatedOperation() {
    delay(10)
  }
}
