import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toChannel
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class KotlinCoroutineTests(private val dispatcher: CoroutineDispatcher) {

  @Trace
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

  @Trace
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

  @Trace
  fun tracedAcrossThreadsWithNested() = runTest {
    val goodDeferred = async { 1 }

    launch {
      goodDeferred.await()
      launch { tracedChild("nested") }
    }
  }

  @Trace
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
  @Trace
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

  @Trace
  fun tracedChild(opName: String) {
    activeSpan().setSpanName(opName)
  }

  private fun <T> runTest(block: suspend CoroutineScope.() -> T): T {
    return runBlocking(dispatcher, block = block)
  }
}

