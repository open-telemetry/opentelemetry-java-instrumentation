/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine

// A tail delegated WithSpan returns to its caller's dispatcher however the callee resumes it.
class TailCallWithSpanDispatcherTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()

    private val callbackExecutor = Executors.newSingleThreadExecutor()

    @JvmStatic
    @AfterAll
    fun shutdown() {
      callbackExecutor.shutdown()
    }
  }

  private val tracer = testing.openTelemetry.getTracer("test")

  @Test
  fun `tail delegated withContext preserves a null result`() {
    runBlocking {
      val gate = CompletableDeferred<Unit>()
      // Runs only once the caller has suspended, so the callee completes after a real suspension.
      launch { gate.complete(Unit) }
      assertThat(annotatedNullableResult(gate)).isNull()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("nullable-result").hasNoParent() },
        )
      },
    )
  }

  @WithSpan("nullable-result")
  private suspend fun annotatedNullableResult(gate: CompletableDeferred<Unit>): String? = withContext<String?>(Dispatchers.IO) {
    gate.await()
    null
  }

  @Test
  fun `tail delegated suspendCancellableCoroutine returns to caller dispatcher`() {
    val callerThread = Thread.currentThread()
    val gate = CountDownLatch(1)
    runBlocking {
      // Runs only once the caller has suspended, so the callback resumes it from another thread.
      launch { gate.countDown() }
      assertThat(annotatedCancellableCallback(gate)).isEqualTo(42)
      assertThat(Thread.currentThread()).isSameAs(callerThread)
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("cancellable-callback").hasNoParent() },
        )
      },
    )
  }

  @WithSpan("cancellable-callback")
  private suspend fun annotatedCancellableCallback(gate: CountDownLatch): Int = suspendCancellableCoroutine { continuation ->
    callbackExecutor.execute {
      gate.await(10, TimeUnit.SECONDS)
      continuation.resume(42)
    }
  }

  @Test
  fun `nested tail delegated WithSpan propagates through withContext`() {
    val rootSpan = tracer.spanBuilder("nested-root").startSpan()
    val callerThread = Thread.currentThread()
    runBlocking(rootSpan.storeInContext(Context.root()).asContextElement()) {
      val gate = CompletableDeferred<Unit>()
      // Runs only once the caller has suspended, so the callee completes after a real suspension.
      launch { gate.complete(Unit) }
      assertThat(annotatedOuterTail(gate)).isEqualTo(42)
      assertThat(Thread.currentThread()).isSameAs(callerThread)
      tracedChild("after-tail")
    }
    rootSpan.end()

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("nested-root").hasNoParent() },
          { it.hasName("outer-tail").hasParent(trace.getSpan(0)) },
          { it.hasName("inner-tail").hasParent(trace.getSpan(1)) },
          { it.hasName("inside-before").hasParent(trace.getSpan(2)) },
          { it.hasName("inside-after").hasParent(trace.getSpan(2)) },
          { it.hasName("after-tail").hasParent(trace.getSpan(0)) },
        )
      },
    )
  }

  @WithSpan("outer-tail")
  private suspend fun annotatedOuterTail(gate: CompletableDeferred<Unit>): Int = annotatedInnerTail(gate)

  @WithSpan("inner-tail")
  private suspend fun annotatedInnerTail(gate: CompletableDeferred<Unit>): Int = withContext(Dispatchers.IO) {
    tracedChild("inside-before")
    gate.await()
    tracedChild("inside-after")
    42
  }

  // The receive keeps its reusable cancellable continuation in the frame's intercepted
  // continuation while the values arrive from another thread.
  @Test
  fun `tail delegated channel receive returns to caller dispatcher`() {
    val expected = receiveFromProducer { unannotatedReceive(it) }
    assertThat(receiveFromProducer { annotatedReceive(it) }).isEqualTo(expected)

    testing.waitAndAssertTraces(singleSpanTraces("receive", expected.size))
  }

  private fun receiveFromProducer(receive: suspend (Channel<Int>) -> Int): List<Int> {
    val callerThread = Thread.currentThread()
    return runBlocking {
      val channel = Channel<Int>()
      launch(Dispatchers.Default) {
        repeat(5) { channel.send(it) }
      }
      List(5) {
        val value = receive(channel)
        assertThat(Thread.currentThread()).isSameAs(callerThread)
        value
      }
    }
  }

  @WithSpan("receive")
  private suspend fun annotatedReceive(channel: Channel<Int>): Int = unannotatedReceive(channel)

  private suspend fun unannotatedReceive(channel: Channel<Int>): Int = channel.receive()

  // The caller's frame alternates between a reusable cancellable continuation (channel receive)
  // and tail delegated calls.
  @Test
  fun `channel receive loop around tail delegated calls`() {
    val expected = receiveLoop { unannotatedIoValue(it) }
    assertThat(receiveLoop { annotatedIoValue(it) }).isEqualTo(expected)

    testing.waitAndAssertTraces(singleSpanTraces("io-value", expected.size))
  }

  private fun receiveLoop(call: suspend (Int) -> Int): List<Int> = runBlocking {
    val channel = Channel<Int>()
    launch {
      repeat(5) { channel.send(it) }
      channel.close()
    }
    val results = mutableListOf<Int>()
    for (value in channel) {
      results += call(value)
    }
    results
  }

  @WithSpan("io-value")
  private suspend fun annotatedIoValue(value: Int): Int = unannotatedIoValue(value)

  private suspend fun unannotatedIoValue(value: Int): Int = withContext(Dispatchers.IO) { value * 2 }

  // The frame intercepts when it is created, before it is known whether the callee suspends; the
  // interceptor must still see one release for each interception.
  @Test
  fun `every intercepted continuation is released`() {
    val interceptor = CountingInterceptor()
    assertThat(runWith(interceptor) { annotatedImmediate() }).isEqualTo(1)
    assertThat(runWith(interceptor) { annotatedSuspending() }).isEqualTo(2)
    assertThat(interceptor.intercepted.get()).isGreaterThan(0)
    assertThat(interceptor.released.get()).isEqualTo(interceptor.intercepted.get())

    testing.waitAndAssertTraces(
      { trace -> trace.hasSpansSatisfyingExactly({ it.hasName("immediate").hasNoParent() }) },
      { trace -> trace.hasSpansSatisfyingExactly({ it.hasName("suspending").hasNoParent() }) },
    )
  }

  private fun runWith(interceptor: ContinuationInterceptor, block: suspend () -> Int): Int {
    val result = CompletableFuture<Int>()
    block.startCoroutine(Continuation(interceptor) { result.complete(it.getOrThrow()) })
    return result.get(10, TimeUnit.SECONDS)
  }

  @WithSpan("immediate")
  private suspend fun annotatedImmediate(): Int = unannotatedImmediate()

  private suspend fun unannotatedImmediate(): Int = suspendCoroutineUninterceptedOrReturn { 1 }

  @WithSpan("suspending")
  private suspend fun annotatedSuspending(): Int = unannotatedSuspending()

  private suspend fun unannotatedSuspending(): Int = suspendCoroutineUninterceptedOrReturn { continuation ->
    val intercepted = continuation.intercepted()
    callbackExecutor.execute { intercepted.resume(2) }
    COROUTINE_SUSPENDED
  }

  // Counts interceptions and releases; wraps each continuation so that the runtime releases it.
  private class CountingInterceptor :
    AbstractCoroutineContextElement(ContinuationInterceptor),
    ContinuationInterceptor {
    val intercepted = AtomicInteger()
    val released = AtomicInteger()

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
      intercepted.incrementAndGet()
      return object : Continuation<T> by continuation {}
    }

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
      released.incrementAndGet()
    }
  }

  private fun singleSpanTraces(name: String, count: Int): List<Consumer<TraceAssert>> = List(count) {
    Consumer { trace -> trace.hasSpansSatisfyingExactly({ it.hasName(name).hasNoParent() }) }
  }

  private fun tracedChild(name: String) {
    tracer.spanBuilder(name).startSpan().end()
  }
}
