/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
        // The callee may signal before the caller has suspended; wait until the caller's thread is
        // free, so the callee can not complete before the caller suspends and skip the dispatch.
        callerExecutor.submit {}.get(10, TimeUnit.SECONDS)
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

  // Under a withContext that keeps the dispatcher, the caller is dispatched again after the callee
  // completes. Cancelled while that last dispatch is queued, it fails or continues as it would
  // without the annotation, depending on how the callee resumed it.
  @Test
  fun `caller under withContext cancelled before running after withContext from another dispatcher`() {
    val expected = continuesWhenCancelledBeforeRunning { started, release -> unannotatedIoReturn(started, release) }
    assertThat(expected).isFalse()
    assertThat(continuesWhenCancelledBeforeRunning { started, release -> annotatedIoReturn(started, release) })
      .isEqualTo(expected)

    assertTwoTraces("io-return")
  }

  @Test
  fun `caller under withContext on an immediate dispatcher cancelled before running after withContext from another dispatcher`() {
    val expected = continuesWhenCancelledBeforeRunning(immediate = true) { started, release -> unannotatedIoReturn(started, release) }
    assertThat(expected).isFalse()
    assertThat(continuesWhenCancelledBeforeRunning(immediate = true) { started, release -> annotatedIoReturn(started, release) })
      .isEqualTo(expected)

    assertTwoTraces("io-return")
  }

  @Test
  fun `caller under withContext cancelled before running after nested tail calls returning from another dispatcher`() {
    val expected = continuesWhenCancelledBeforeRunning { started, release -> unannotatedOuterIoReturn(started, release) }
    assertThat(expected).isFalse()
    assertThat(continuesWhenCancelledBeforeRunning { started, release -> annotatedOuterIoReturn(started, release) })
      .isEqualTo(expected)

    // the scenario runs twice with the annotation: once to count dispatches, once cancelling
    val nested: (TraceAssert) -> Unit = { trace ->
      trace.hasSpansSatisfyingExactly(
        { it.hasName("outer-io-return").hasNoParent() },
        { it.hasName("io-return").hasParent(trace.getSpan(0)) },
      )
    }
    testing.waitAndAssertTraces(nested, nested)
  }

  @WithSpan("outer-io-return")
  private suspend fun annotatedOuterIoReturn(started: CountDownLatch, release: CountDownLatch): Int = annotatedIoReturn(started, release)

  private suspend fun unannotatedOuterIoReturn(started: CountDownLatch, release: CountDownLatch): Int = unannotatedIoReturn(started, release)

  @WithSpan("io-return")
  private suspend fun annotatedIoReturn(started: CountDownLatch, release: CountDownLatch): Int = unannotatedIoReturn(started, release)

  private suspend fun unannotatedIoReturn(started: CountDownLatch, release: CountDownLatch): Int = withContext(Dispatchers.IO) {
    started.countDown()
    release.await(10, TimeUnit.SECONDS)
    42
  }

  @Test
  fun `caller under withContext cancelled before running after suspendCancellableCoroutine`() {
    val expected = continuesWhenCancelledBeforeRunning { started, release -> unannotatedCancellableCallback(started, release) }
    assertThat(expected).isFalse()
    assertThat(continuesWhenCancelledBeforeRunning { started, release -> annotatedCancellableCallback(started, release) })
      .isEqualTo(expected)

    assertTwoTraces("cancellable-callback")
  }

  @WithSpan("cancellable-callback")
  private suspend fun annotatedCancellableCallback(started: CountDownLatch, release: CountDownLatch): Int = unannotatedCancellableCallback(started, release)

  private suspend fun unannotatedCancellableCallback(started: CountDownLatch, release: CountDownLatch): Int = suspendCancellableCoroutine { continuation ->
    started.countDown()
    callbackExecutor.execute {
      release.await(10, TimeUnit.SECONDS)
      continuation.resume(42)
    }
  }

  // An element received from a channel is either delivered to the caller or passed to the
  // channel's undelivered element handler, never lost.
  @Test
  fun `caller under withContext cancelled before running after receive delivers or hands back the element`() {
    assertThat(deliveredOrHandedBack { started, release, channel -> unannotatedReceive(started, release, channel) }).isTrue()
    assertThat(deliveredOrHandedBack { started, release, channel -> annotatedReceive(started, release, channel) }).isTrue()

    assertTwoTraces("receive")
  }

  private fun deliveredOrHandedBack(call: suspend (CountDownLatch, CountDownLatch, Channel<Int>) -> Int): Boolean {
    var undelivered = AtomicInteger(-1)
    var channel = Channel<Int>(1) { undelivered.set(it) }
    val dispatches = runCancellingAt(Int.MAX_VALUE, false) { started, release -> call(started, release, channel) }.second
    undelivered = AtomicInteger(-1)
    channel = Channel(1) { undelivered.set(it) }
    val continued = runCancellingAt(dispatches, false) { started, release -> call(started, release, channel) }.first
    return continued || undelivered.get() == 42
  }

  @WithSpan("receive")
  private suspend fun annotatedReceive(started: CountDownLatch, release: CountDownLatch, channel: Channel<Int>): Int = unannotatedReceive(started, release, channel)

  private suspend fun unannotatedReceive(started: CountDownLatch, release: CountDownLatch, channel: Channel<Int>): Int {
    started.countDown()
    callbackExecutor.execute {
      release.await(10, TimeUnit.SECONDS)
      channel.trySend(42)
    }
    return channel.receive()
  }

  @Test
  fun `caller under withContext cancelled before running after an atomic resumption`() {
    val expected = continuesWhenCancelledBeforeRunning { started, release -> unannotatedAtomicCallback(started, release) }
    assertThat(expected).isTrue()
    assertThat(continuesWhenCancelledBeforeRunning { started, release -> annotatedAtomicCallback(started, release) })
      .isEqualTo(expected)

    assertTwoTraces("atomic-callback")
  }

  @WithSpan("atomic-callback")
  private suspend fun annotatedAtomicCallback(started: CountDownLatch, release: CountDownLatch): Int = unannotatedAtomicCallback(started, release)

  private suspend fun unannotatedAtomicCallback(started: CountDownLatch, release: CountDownLatch): Int = suspendCoroutineUninterceptedOrReturn { continuation ->
    val intercepted = continuation.intercepted()
    started.countDown()
    callbackExecutor.execute {
      release.await(10, TimeUnit.SECONDS)
      intercepted.resume(42)
    }
    COROUTINE_SUSPENDED
  }

  // the scenario runs twice with the annotation: once to count dispatches, once cancelling
  private fun assertTwoTraces(spanName: String) {
    testing.waitAndAssertTraces(
      { trace -> trace.hasSpansSatisfyingExactly({ it.hasName(spanName).hasNoParent() }) },
      { trace -> trace.hasSpansSatisfyingExactly({ it.hasName(spanName).hasNoParent() }) },
    )
  }

  // Counts the caller's dispatches once the callee is released, then runs the scenario again and
  // cancels the caller just before the last of them, the one that runs the caller.
  private fun continuesWhenCancelledBeforeRunning(immediate: Boolean = false, call: suspend (CountDownLatch, CountDownLatch) -> Int): Boolean {
    val dispatches = runCancellingAt(Int.MAX_VALUE, immediate, call).second
    return runCancellingAt(dispatches, immediate, call).first
  }

  private fun runCancellingAt(cancelAt: Int, immediate: Boolean, call: suspend (CountDownLatch, CountDownLatch) -> Int): Pair<Boolean, Int> {
    val callerThread = arrayOfNulls<Thread>(1)
    val callerExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable).also { callerThread[0] = it } }
    val caller = arrayOfNulls<Job>(1)
    val dispatcher = CancellingDispatcher(callerExecutor.asCoroutineDispatcher(), cancelAt, { caller[0]!!.cancel() }) {
      // like Dispatchers.Main.immediate, runs inline when already on its thread
      !immediate || Thread.currentThread() !== callerThread[0]
    }
    val continued = AtomicBoolean()
    try {
      runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        caller[0] = launch(dispatcher) {
          withContext(CoroutineName("caller")) {
            call(started, release)
            continued.set(true)
          }
        }
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
        // wait until the caller has suspended
        callerExecutor.submit {}.get(10, TimeUnit.SECONDS)
        dispatcher.watch()
        release.countDown()
        caller[0]!!.join()
      }
    } finally {
      callerExecutor.shutdown()
    }
    return continued.get() to dispatcher.dispatches.get()
  }

  // Counts dispatches after watch() is called and cancels just before the given one.
  private class CancellingDispatcher(
    private val delegate: CoroutineDispatcher,
    private val cancelAt: Int,
    private val cancel: () -> Unit,
    private val dispatchNeeded: () -> Boolean,
  ) : CoroutineDispatcher() {
    val dispatches = AtomicInteger()

    @Volatile private var watching = false

    fun watch() {
      watching = true
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = dispatchNeeded()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      if (watching && dispatches.incrementAndGet() == cancelAt) {
        cancel()
      }
      delegate.dispatch(context, block)
    }
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
