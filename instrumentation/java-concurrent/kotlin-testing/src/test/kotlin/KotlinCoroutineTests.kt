/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer
import io.opentelemetry.trace.TracingContextUtils.currentContextWith
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toChannel
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class KotlinCoroutineTests(private val dispatcher: CoroutineDispatcher) {
  val tracer: Tracer = OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto")

  fun tracedAcrossChannels() = runTest {

    val producer = produce {
      repeat(3) {
        tracedChild("produce_$it")
        send(it)
      }
    }

    val actor = actor<Int> {
      consumeEach {
        tracedChild("consume_$it")
      }
    }

    producer.toChannel(actor)
    actor.close()
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

  fun tracedChild(opName: String) {
    tracer.spanBuilder(opName).startSpan().end()
  }

  private fun <T> runTest(block: suspend CoroutineScope.() -> T): T {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope = currentContextWith(parentSpan)
    try {
      return runBlocking(dispatcher, block = block)
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }
}

