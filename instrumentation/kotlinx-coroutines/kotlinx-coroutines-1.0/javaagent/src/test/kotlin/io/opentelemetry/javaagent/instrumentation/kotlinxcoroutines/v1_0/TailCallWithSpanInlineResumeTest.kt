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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

// Callers resumed inline after a tail delegated WithSpan (an Unconfined caller, a completion
// without an interceptor) see their own context, not the span that just ended.
class TailCallWithSpanInlineResumeTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  private val tracer = testing.openTelemetry.getTracer("test")

  @Test
  fun `unconfined caller keeps its context after tail delegated WithSpan`() {
    val outer = tracer.spanBuilder("outer").startSpan()
    val after = runBlocking {
      withContext(Dispatchers.Unconfined + outer.storeInContext(Context.root()).asContextElement()) {
        annotatedIoTail()
        Span.current().spanContext
      }
    }
    outer.end()
    assertThat(after).isEqualTo(outer.spanContext)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("outer").hasNoParent() },
          { it.hasName("io-tail").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  @Test
  fun `completion without context element sees the parent context, not the ended span`() {
    val root = tracer.spanBuilder("thread-only").startSpan()
    try {
      // Without annotations the completion sees an empty context; the annotation restores the
      // parent context that was current when the annotated method was entered.
      assertThat(currentAfter(root) { unannotatedIoTail() }.isValid).isFalse()
      assertThat(currentAfter(root) { annotatedIoTail() }).isEqualTo(root.spanContext)
    } finally {
      root.end()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("thread-only").hasNoParent() },
          { it.hasName("io-tail").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  private fun currentAfter(root: Span, call: suspend () -> Int): SpanContext {
    val completed = CompletableFuture<SpanContext>()
    val completion = object : Continuation<Int> {
      override val context: CoroutineContext = EmptyCoroutineContext
      override fun resumeWith(result: Result<Int>) {
        result.fold(
          onSuccess = { completed.complete(Span.current().spanContext) },
          onFailure = { completed.completeExceptionally(it) }
        )
      }
    }
    root.makeCurrent().use {
      call.startCoroutineUninterceptedOrReturn(completion)
    }
    return completed.get(10, TimeUnit.SECONDS)
  }

  // A continuation resumed without interception runs the caller inline, before resume returns.
  @Test
  fun `caller resumed without interception runs before resume returns`() {
    val expected = valueRightAfterResume { unannotatedUnintercepted(it) }
    assertThat(expected).isEqualTo(7)
    assertThat(valueRightAfterResume { annotatedUnintercepted(it) }).isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("unintercepted").hasNoParent() },
        )
      },
    )
  }

  private fun valueRightAfterResume(call: suspend ((Continuation<Int>) -> Unit) -> Int): Int {
    var value = 0
    var observed = -1
    runBlocking {
      var continuation: Continuation<Int>? = null
      launch(start = CoroutineStart.UNDISPATCHED) {
        value = call { continuation = it }
      }
      continuation!!.resume(7)
      observed = value
    }
    return observed
  }

  @WithSpan("unintercepted")
  private suspend fun annotatedUnintercepted(register: (Continuation<Int>) -> Unit): Int = unannotatedUnintercepted(register)

  private suspend fun unannotatedUnintercepted(register: (Continuation<Int>) -> Unit): Int = suspendCoroutineUninterceptedOrReturn { continuation ->
    register(continuation)
    COROUTINE_SUSPENDED
  }

  @WithSpan("io-tail")
  private suspend fun annotatedIoTail(): Int = unannotatedIoTail()

  private suspend fun unannotatedIoTail(): Int = withContext(Dispatchers.IO) {
    delay(10)
    42
  }
}
