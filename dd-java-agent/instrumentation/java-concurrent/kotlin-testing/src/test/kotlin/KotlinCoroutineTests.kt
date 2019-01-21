import datadog.trace.api.Trace
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class KotlinCoroutineTests(private val dispatcher: CoroutineDispatcher) {

  @Trace
  fun tracedAcrossChannels(): Int = runTest {
    val producer = produce {
      repeat(3){
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

    7
  }

  @Trace
  fun tracePreventedByCancellation(): Int {

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

    return 2
  }

  @Trace
  fun tracedAcrossThreadsWithNested(): Int = runTest {
    val goodDeferred = async { 1 }

    launch {
      goodDeferred.await()
      launch { tracedChild("nested") }
    }

    2
  }

  @Trace
  fun traceWithDeferred(): Int = runTest {

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

    5
  }

   /**
   * @return Number of expected spans in the trace
   */
  @Trace
  fun tracedWithDeferredFirstCompletions(): Int = runTest {

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

   4
  }

  @Trace
  fun tracedChild(opName: String){
    GlobalTracer.get().activeSpan().setOperationName(opName)
  }

  private fun <T> runTest(asyncPropagation: Boolean = true, block: suspend CoroutineScope.()->T ): T {
    GlobalTracer.get().scopeManager().active().setAsyncPropagation(asyncPropagation)
    return runBlocking(dispatcher,block = block)
  }

  private fun Scope.setAsyncPropagation(value: Boolean): Unit =
    (this as TraceScope).setAsyncPropagation(value)
}

