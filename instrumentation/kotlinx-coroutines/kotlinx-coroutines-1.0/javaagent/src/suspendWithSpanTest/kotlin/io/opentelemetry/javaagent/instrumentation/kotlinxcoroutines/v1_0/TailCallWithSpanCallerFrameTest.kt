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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

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
  fun `thread context element of an inner withContext does not leak to the outer code after an unintercepted resume`() {
    val inner = tracer.spanBuilder("inner").startSpan()
    try {
      val expected = tagAfterUninterceptedResume(inner) { unannotatedUnintercepted(it) }
      assertThat(expected).isNull()
      assertThat(tagAfterUninterceptedResume(inner) { annotatedUnintercepted(it) }).isEqualTo(expected)
    } finally {
      inner.end()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("inner").hasNoParent() },
          { it.hasName("unintercepted").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  private fun tagAfterUninterceptedResume(inner: Span, call: suspend (Executor) -> Int): String? {
    val executor = Executors.newSingleThreadExecutor()
    try {
      return CompletableFuture.supplyAsync {
        runBlocking {
          withContext(Dispatchers.Unconfined) {
            withContext(TagElement("inner") + inner.storeInContext(Context.root()).asContextElement()) {
              call(executor)
            }
            tag.get()
          }
        }
      }.get(10, TimeUnit.SECONDS)
    } finally {
      executor.shutdown()
    }
  }

  @WithSpan("unintercepted")
  private suspend fun annotatedUnintercepted(executor: Executor) = unannotatedUnintercepted(executor)

  // resumed without interception, so no runtime frame applies the callee's context
  private suspend fun unannotatedUnintercepted(executor: Executor): Int {
    val value = suspendCoroutineUninterceptedOrReturn { continuation ->
      executor.execute { continuation.resume(1) }
      COROUTINE_SUSPENDED
    }
    return value + 1
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

  @Test
  fun `inner withContext on an immediate dispatcher leaves its thread clean`() {
    val inner = tracer.spanBuilder("inner").startSpan()
    try {
      val expected = threadStateAfterInnerWithContext(inner) { unannotatedDelayFrame() }
      assertThat(expected).isEqualTo(null to SpanContext.getInvalid())
      assertThat(threadStateAfterInnerWithContext(inner) { annotatedDelayFrame() }).isEqualTo(expected)
    } finally {
      inner.end()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("inner").hasNoParent() },
          { it.hasName("delay-frame").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  // The thread local state the dispatcher thread is left with once the coroutine completes.
  private fun threadStateAfterInnerWithContext(inner: Span, call: suspend () -> Int): Pair<String?, SpanContext> {
    ImmediateDispatcher().use { dispatcher ->
      runBlocking {
        withContext(dispatcher) {
          withContext(TagElement("inner") + inner.storeInContext(Context.root()).asContextElement()) {
            call()
          }
        }
      }
      return dispatcher.runOnThread { tag.get() to Span.current().spanContext }
    }
  }

  @Test
  fun `inner withContext leaves its thread clean when the callee ends in coroutineScope`() {
    val inner = tracer.spanBuilder("inner").startSpan()
    try {
      val expected = threadStateAfterInnerScope(inner) { unannotatedCoroutineScope() }
      assertThat(expected).containsExactly(null to SpanContext.getInvalid(), null to SpanContext.getInvalid())
      assertThat(threadStateAfterInnerScope(inner) { annotatedCoroutineScope() }).isEqualTo(expected)
    } finally {
      inner.end()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("inner").hasNoParent() },
          { it.hasName("coroutine-scope").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  @Test
  fun `inner withContext leaves its thread clean when the callee ends in supervisorScope`() {
    val inner = tracer.spanBuilder("inner").startSpan()
    try {
      val expected = threadStateAfterInnerScope(inner) { unannotatedSupervisorScope() }
      assertThat(expected).containsExactly(null to SpanContext.getInvalid(), null to SpanContext.getInvalid())
      assertThat(threadStateAfterInnerScope(inner) { annotatedSupervisorScope() }).isEqualTo(expected)
    } finally {
      inner.end()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("inner").hasNoParent() },
          { it.hasName("supervisor-scope").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  // The thread local state of the code after the inner withContext, and of the thread once
  // runBlocking returns. Runs on its own thread, so a leak does not reach other tests.
  private fun threadStateAfterInnerScope(inner: Span, call: suspend () -> Int): List<Pair<String?, SpanContext>> {
    val executor = Executors.newSingleThreadExecutor()
    try {
      return CompletableFuture.supplyAsync({
        val afterWithContext = runBlocking(Context.root().asContextElement()) {
          withContext(TagElement("inner") + inner.storeInContext(Context.root()).asContextElement()) {
            call()
          }
          tag.get() to Span.current().spanContext
        }
        listOf(afterWithContext, tag.get() to Span.current().spanContext)
      }, executor).get(10, TimeUnit.SECONDS)
    } finally {
      executor.shutdown()
    }
  }

  @WithSpan("coroutine-scope")
  private suspend fun annotatedCoroutineScope() = unannotatedCoroutineScope()

  // the scope's body completes last, so the scope resumes the caller from within the body
  private suspend fun unannotatedCoroutineScope(): Int = coroutineScope {
    delay(10)
    1
  }

  @WithSpan("supervisor-scope")
  private suspend fun annotatedSupervisorScope() = unannotatedSupervisorScope()

  private suspend fun unannotatedSupervisorScope(): Int = supervisorScope {
    delay(10)
    1
  }

  @Test
  fun `caller on a dispatcher that can not dispatch resumes after tail delegated WithSpan`() {
    assertThat(resultOnUnconfinedWrapper { unannotatedDelayFrame() }).isEqualTo(1)
    assertThat(resultOnUnconfinedWrapper { annotatedDelayFrame() }).isEqualTo(1)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("delay-frame").hasNoParent() },
        )
      },
    )
  }

  private fun resultOnUnconfinedWrapper(call: suspend () -> Int): Int = CompletableFuture.supplyAsync {
    runBlocking {
      withContext(UnconfinedWrapper) {
        withContext(TagElement("inner")) {
          call()
        }
      }
    }
  }.get(10, TimeUnit.SECONDS)

  // Like UnconfinedTestDispatcher, never needs a dispatch and rejects one.
  private object UnconfinedWrapper : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false

    override fun dispatch(context: CoroutineContext, block: Runnable) = Dispatchers.Unconfined.dispatch(context, block)
  }

  @WithSpan("delay-frame")
  private suspend fun annotatedDelayFrame() = unannotatedDelayFrame()

  // not a tail call, so the callee has a frame of its own that the dispatcher resumes
  private suspend fun unannotatedDelayFrame(): Int {
    delay(10)
    return 1
  }

  // Like Dispatchers.Main.immediate, runs the coroutine inline when already on its thread.
  private class ImmediateDispatcher :
    CoroutineDispatcher(),
    AutoCloseable {
    @Volatile private var thread: Thread? = null
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable).also { thread = it } }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = Thread.currentThread() !== thread

    override fun dispatch(context: CoroutineContext, block: Runnable) = executor.execute(block)

    fun <T> runOnThread(block: () -> T): T = executor.submit(Callable(block)).get(10, TimeUnit.SECONDS)

    override fun close() = executor.shutdown()
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
