/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.concurrent.TimeUnit

class KotlinCoroutineTests(private val dispatcher: CoroutineDispatcher) {
  val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  fun tracedAcrossChannels() = runTest {

    val producer = produce {
      repeat(3) {
        tracedChild("produce_$it")
        send(it)
      }
    }

    producer.consumeAsFlow().onEach {
      tracedChild("consume_$it")
    }.collect()
  }

  fun tracePreventedByCancellation() {

    kotlin.runCatching {
      runTest {
        tracedChild("preLaunch")

        launch(start = CoroutineStart.UNDISPATCHED) {
          throw Exception("Child Error")
        }

        yield()

        tracedChild("postLaunch")
      }
    }
  }

  fun tracedAcrossThreadsWithNested() = runTest {
    val goodDeferred = async { 1 }

    launch {
      goodDeferred.await()
      launch { tracedChild("nested") }
    }
  }

  fun traceWithDeferred() = runTest {

    val keptPromise = CompletableDeferred<Boolean>()
    val brokenPromise = CompletableDeferred<Boolean>()
    val afterPromise = async {
      keptPromise.await()
      tracedChild("keptPromise")
    }
    val afterPromise2 = async {
      keptPromise.await()
      tracedChild("keptPromise2")
    }
    val failedAfterPromise = async {
      brokenPromise
        .runCatching { await() }
        .onFailure { tracedChild("brokenPromise") }
    }

    launch {
      tracedChild("future1")
      keptPromise.complete(true)
      brokenPromise.completeExceptionally(IllegalStateException())
    }

    listOf(afterPromise, afterPromise2, failedAfterPromise).awaitAll()
  }

  /**
   * @return Number of expected spans in the trace
   */
  fun tracedWithDeferredFirstCompletions() = runTest {

    val children = listOf(
      async {
        tracedChild("timeout1")
        false
      },
      async {
        tracedChild("timeout2")
        false
      },
      async {
        tracedChild("timeout3")
        true
      }
    )

    withTimeout(TimeUnit.SECONDS.toMillis(30)) {
      select<Boolean> {
        children.forEach { child ->
          child.onAwait { it }
        }
      }
    }
  }

  fun launchConcurrentSuspendFunctions(numIters: Int) {
    runBlocking {
      for (i in 0 until numIters) {
        GlobalScope.launch {
          a(i.toLong())
        }
        GlobalScope.launch {
          b(i.toLong())
        }
      }
    }
  }

  suspend fun a(iter: Long) {
    var span = tracer.spanBuilder("a").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
      a2(iter)
    }
    span.end()
  }

  suspend fun a2(iter: Long) {
    var span = tracer.spanBuilder("a2").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
    }
    span.end()
  }

  suspend fun b(iter: Long) {
    var span = tracer.spanBuilder("b").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
      b2(iter)
    }
    span.end()
  }

  suspend fun b2(iter: Long) {
    var span = tracer.spanBuilder("b2").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
    }
    span.end()
  }

  fun tracedChild(opName: String) {
    tracer.spanBuilder(opName).startSpan().end()
  }

  private fun <T> runTest(block: suspend CoroutineScope.() -> T): T {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope = parentSpan.makeCurrent()
    try {
      return runBlocking(dispatcher, block = block)
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }
}
