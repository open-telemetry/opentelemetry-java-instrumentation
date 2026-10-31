/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

// Behavior that depends on the coroutine caller frame chain, compared with and without the
// annotation.
class TailCallWithSpanCallerFrameTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()

    private val tag = ThreadLocal<String?>()
  }

  private val tracer = testing.openTelemetry.getTracer("test")

  @Test
  fun `recovered stack trace includes the caller of a tail delegated WithSpan`() {
    val expected = recoveredMethods { unannotatedThrowing() }
    assertThat(expected).contains("throwingCaller")
    assertThat(recoveredMethods { annotatedThrowing() }).isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("throwing").hasNoParent().hasStatus(StatusData.error()) },
        )
      },
    )
  }

  private fun recoveredMethods(call: suspend () -> Int): List<String> = runBlocking {
    try {
      throwingCaller(call)
      emptyList()
    } catch (e: IllegalStateException) {
      e.stackTrace.map { it.methodName }
    }
  }

  private suspend fun throwingCaller(call: suspend () -> Int): Int {
    val result = call()
    return result + 1
  }

  @WithSpan("throwing")
  private suspend fun annotatedThrowing(): Int = unannotatedThrowing()

  private suspend fun unannotatedThrowing(): Int = withContext(Dispatchers.IO) {
    delay(10)
    throw IllegalStateException("boom")
  }

  @Test
  fun `thread context element of an inner withContext does not leak to the outer code`() {
    val expected = tagAfterInnerWithContext { unannotatedDelay() }
    assertThat(expected).isNull()
    assertThat(tagAfterInnerWithContext { annotatedDelay() }).isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("delay").hasNoParent() },
        )
      },
    )
  }

  private fun tagAfterInnerWithContext(call: suspend () -> Unit): String? = runBlocking {
    withContext(TagElement("inner")) {
      call()
    }
    tag.get()
  }

  @Test
  fun `otel context of an inner withContext does not leak to the outer code`() {
    val inner = tracer.spanBuilder("inner").startSpan()
    try {
      val expected = spanAfterInnerWithContext(inner) { unannotatedDelay() }
      assertThat(expected).isNotEqualTo(inner.spanContext)
      assertThat(spanAfterInnerWithContext(inner) { annotatedDelay() }).isEqualTo(expected)
    } finally {
      inner.end()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("inner").hasNoParent() },
          { it.hasName("delay").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  private fun spanAfterInnerWithContext(inner: Span, call: suspend () -> Unit): SpanContext = runBlocking {
    withContext(inner.storeInContext(Context.root()).asContextElement()) {
      call()
    }
    Span.current().spanContext
  }

  @WithSpan("delay")
  private suspend fun annotatedDelay() = unannotatedDelay()

  private suspend fun unannotatedDelay() = delay(10)

  private class TagElement(private val value: String) : ThreadContextElement<String?> {
    companion object Key : CoroutineContext.Key<TagElement>

    override val key: CoroutineContext.Key<TagElement> get() = Key

    override fun updateThreadContext(context: CoroutineContext): String? {
      val previous = tag.get()
      tag.set(value)
      return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String?) {
      tag.set(oldState)
    }
  }
}
