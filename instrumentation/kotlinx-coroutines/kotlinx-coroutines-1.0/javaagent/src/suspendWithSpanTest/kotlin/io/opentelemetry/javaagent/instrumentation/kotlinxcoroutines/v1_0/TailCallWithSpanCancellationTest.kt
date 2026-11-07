/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

// A cancelled caller of a tail delegated WithSpan continues or fails exactly as it would without
// the annotation. Each test runs the same scenario with and without it and compares the outcome.
class TailCallWithSpanCancellationTest {

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

  // Returning from another dispatcher is cancellable: the cancelled caller fails instead of
  // continuing.
  @Test
  fun `cancelled caller returning from another dispatcher`() {
    val expected = continuesAfterCancellation { started, release -> unannotatedIoNonCancellable(started, release) }
    assertThat(expected).isFalse()
    assertThat(continuesAfterCancellation { started, release -> annotatedIoNonCancellable(started, release) })
      .isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("io-non-cancellable").hasNoParent() },
        )
      },
    )
  }

  @WithSpan("io-non-cancellable")
  private suspend fun annotatedIoNonCancellable(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = unannotatedIoNonCancellable(started, release)

  private suspend fun unannotatedIoNonCancellable(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = withContext(Dispatchers.IO + NonCancellable) {
    started.complete(Unit)
    release.await(10, TimeUnit.SECONDS)
    42
  }

  // Returning on the same dispatcher adds no cancellation check: the cancelled caller continues.
  @Test
  fun `cancelled caller returning on the same dispatcher`() {
    val expected = continuesAfterCancellation { started, release -> unannotatedSameDispatcherNonCancellable(started, release) }
    assertThat(expected).isTrue()
    assertThat(continuesAfterCancellation { started, release -> annotatedSameDispatcherNonCancellable(started, release) })
      .isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("same-dispatcher-non-cancellable").hasNoParent() },
        )
      },
    )
  }

  @WithSpan("same-dispatcher-non-cancellable")
  private suspend fun annotatedSameDispatcherNonCancellable(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = unannotatedSameDispatcherNonCancellable(started, release)

  private suspend fun unannotatedSameDispatcherNonCancellable(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = withContext(NonCancellable) {
    started.complete(Unit)
    // Lets the caller's event loop run the cancellation while this block is suspended.
    withContext(Dispatchers.IO) { release.await(10, TimeUnit.SECONDS) }
    42
  }

  // Resuming an intercepted continuation from another thread is not cancellable (as with
  // suspendCoroutine): the cancelled caller still receives the result.
  @Test
  fun `cancelled caller resumed by a callback`() {
    val expected = continuesAfterCancellation { started, release -> unannotatedCallback(started, release) }
    assertThat(expected).isTrue()
    assertThat(continuesAfterCancellation { started, release -> annotatedCallback(started, release) })
      .isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("callback").hasNoParent() },
        )
      },
    )
  }

  @WithSpan("callback")
  private suspend fun annotatedCallback(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = unannotatedCallback(started, release)

  private suspend fun unannotatedCallback(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = suspendCoroutineUninterceptedOrReturn { continuation ->
    val intercepted = continuation.intercepted()
    started.complete(Unit)
    callbackExecutor.execute {
      release.await(10, TimeUnit.SECONDS)
      intercepted.resume(42)
    }
    COROUTINE_SUSPENDED
  }

  // Cancels the caller while it waits for the callee, then lets the callee complete.
  private fun continuesAfterCancellation(call: suspend (CompletableDeferred<Unit>, CountDownLatch) -> Int): Boolean {
    var continued = false
    runBlocking {
      val started = CompletableDeferred<Unit>()
      val release = CountDownLatch(1)
      val caller = launch {
        call(started, release)
        continued = true
      }
      started.await()
      caller.cancel()
      release.countDown()
      caller.join()
    }
    return continued
  }

  // The caller is cancelled after the callee completed but before the caller's dispatcher runs it:
  // the cancellation check happens when the caller runs, not when it is queued.
  @Test
  fun `caller cancelled while its resumption is queued`() {
    val expected = continuesWhenCancelledInQueue { started, release -> unannotatedIoTail(started, release) }
    assertThat(expected).isFalse()
    assertThat(continuesWhenCancelledInQueue { started, release -> annotatedIoTail(started, release) })
      .isEqualTo(expected)

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("io-tail").hasNoParent() },
        )
      },
    )
  }

  private fun continuesWhenCancelledInQueue(call: suspend (CompletableDeferred<Unit>, CountDownLatch) -> Int): Boolean {
    val callerExecutor = Executors.newSingleThreadExecutor()
    val dispatcher = QueueWatchingDispatcher(callerExecutor.asCoroutineDispatcher())
    val continued = AtomicBoolean()
    try {
      runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val caller = launch(dispatcher) {
          call(started, release)
          continued.set(true)
        }
        started.await()
        // Keep the caller's thread busy so that its resumption stays queued until it is cancelled.
        val unblock = CountDownLatch(1)
        callerExecutor.execute { unblock.await(10, TimeUnit.SECONDS) }
        dispatcher.watch()
        release.countDown()
        // fails when the caller is resumed without going through its dispatcher
        assertThat(dispatcher.awaitDispatch()).isTrue()
        caller.cancel()
        unblock.countDown()
        caller.join()
      }
    } finally {
      callerExecutor.shutdown()
    }
    return continued.get()
  }

  @WithSpan("io-tail")
  private suspend fun annotatedIoTail(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = unannotatedIoTail(started, release)

  private suspend fun unannotatedIoTail(started: CompletableDeferred<Unit>, release: CountDownLatch): Int = withContext(Dispatchers.IO) {
    started.complete(Unit)
    release.await(10, TimeUnit.SECONDS)
    42
  }

  // Signals the first dispatch after watch() is called.
  private class QueueWatchingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private val dispatched = CountDownLatch(1)

    @Volatile private var watching = false

    fun watch() {
      watching = true
    }

    fun awaitDispatch(): Boolean = dispatched.await(10, TimeUnit.SECONDS)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      if (watching) {
        dispatched.countDown()
      }
      delegate.dispatch(context, block)
    }
  }
}
