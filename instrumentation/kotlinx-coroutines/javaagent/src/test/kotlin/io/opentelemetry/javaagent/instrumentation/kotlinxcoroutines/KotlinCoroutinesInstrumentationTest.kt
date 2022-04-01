/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.reactor.ContextPropagationOperator
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalCoroutinesApi
class KotlinCoroutinesInstrumentationTest {

  companion object {
    val threadPool = Executors.newFixedThreadPool(2)
    val singleThread = Executors.newSingleThreadExecutor()
  }

  @AfterAll
  fun shutdown() {
    threadPool.shutdown()
    singleThread.shutdown()
  }

  @RegisterExtension val testing = AgentInstrumentationExtension.create()

  val tracer = testing.openTelemetry.getTracer("test")

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `traced across channels`(dispatcher: DispatcherWrapper) {
    runTest(dispatcher) {
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

    testing.waitAndAssertTraces(
      { trace ->
        // TODO(anuraaga): Need hasSpansSatisfyingExactlyInAnyOrder sometimes
        trace.satisfiesExactlyInAnyOrder(
          Consumer {
            assertThat(it)
              .hasName("parent")
              .hasNoParent()
          },
          Consumer {
            assertThat(it)
              .hasName("produce_0")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("consume_0")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("produce_1")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("consume_1")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("produce_2")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("consume_2")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `cancellation prevents trace`(dispatcher: DispatcherWrapper) {
    runCatching {
      runTest(dispatcher) {
        tracedChild("preLaunch")

        launch(start = CoroutineStart.UNDISPATCHED) {
          throw Exception("Child Error")
        }

        yield()

        tracedChild("postLaunch")
      }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("preLaunch")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `propagates across nested jobs`(dispatcher: DispatcherWrapper) {
    runTest(dispatcher) {
      val goodDeferred = async { 1 }

      launch {
        goodDeferred.await()
        launch { tracedChild("nested") }
      }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("nested")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @Test
  fun `deferred completion`() {
    runTest(Dispatchers.Default) {
      val keptPromise = CompletableDeferred<Boolean>()
      val brokenPromise = CompletableDeferred<Boolean>()
      val afterPromise = async {
        keptPromise.await()
        tracedChild("keptPromise")
      }
      val afterPromise2 = async {
        listOf(afterPromise, keptPromise).awaitAll()
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

    testing.waitAndAssertTraces(
      { trace ->
        trace.satisfiesExactlyInAnyOrder(
          Consumer {
            assertThat(it).hasName("parent")
              .hasNoParent()
          },
          Consumer {
            assertThat(it).hasName("future1")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it).hasName("keptPromise")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it).hasName("keptPromise2")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it).hasName("brokenPromise")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @Test
  fun `first completed deferred`() {
    runTest(Dispatchers.Default) {
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

    testing.waitAndAssertTraces(
      { trace ->
        // TODO(anuraaga): Need hasSpansSatisfyingExactlyInAnyOrder sometimes
        trace.satisfiesExactlyInAnyOrder(
          Consumer {
            assertThat(it)
              .hasName("parent")
              .hasNoParent()
          },
          Consumer {
            assertThat(it)
              .hasName("timeout1")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("timeout2")
              .hasParent(trace.getSpan(0))
          },
          Consumer {
            assertThat(it)
              .hasName("timeout3")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @Test
  fun `concurrent suspend functions`() {
    val numIters = 100
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

    // This generates numIters each of "a calls a2" and "b calls b2" traces.  Each
    // trace should have a single pair of spans (a and a2) and each of those spans
    // should have the same iteration number (attribute "iter").
    // The traces are in some random order, so let's keep track and make sure we see
    // each iteration # exactly once
    val assertions = mutableListOf<Consumer<List<SpanData>>>()
    for (i in 0 until numIters) {
      assertions.add { trace ->
        assertThat(trace).satisfiesExactly(
          Consumer {
            assertThat(it)
              .hasName("a")
              .hasNoParent()
          },
          Consumer {
            assertThat(it)
              .hasName("a2")
              .hasParent(trace.get(0))
          },
        )
      }
      assertions.add { trace ->
        assertThat(trace).satisfiesExactly(
          Consumer {
            assertThat(it)
              .hasName("b")
              .hasNoParent()
          },
          Consumer {
            assertThat(it)
              .hasName("b2")
              .hasParent(trace.get(0))
          },
        )
      }
    }

    await().atMost(Duration.ofSeconds(30)).untilAsserted {
      val traces = testing.waitForTraces(assertions.size)
      assertThat(traces).satisfiesExactlyInAnyOrder(*assertions.toTypedArray())
    }
  }

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `traced mono`(dispatcherWrapper: DispatcherWrapper) {
    runTest(dispatcherWrapper) {
      mono(dispatcherWrapper.dispatcher) {
        tracedChild("child")
      }.awaitSingle()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("child")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `traced mono with context propagation operator`(dispatcherWrapper: DispatcherWrapper) {
    runTest(dispatcherWrapper) {
      val currentContext = Context.current()
      // clear current context to ensure that ContextPropagationOperator is used for context propagation
      withContext(Context.root().asContextElement()) {
        val mono = mono(dispatcherWrapper.dispatcher) {
          // extract context from reactor and propagate it into coroutine
          val reactorContext = coroutineContext[ReactorContext.Key]?.context
          val otelContext = ContextPropagationOperator.getOpenTelemetryContext(reactorContext, Context.current())
          withContext(otelContext.asContextElement()) {
            tracedChild("child")
          }
        }
        ContextPropagationOperator.runWithContext(mono, currentContext).awaitSingle()
      }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("child")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `traced flux`(dispatcherWrapper: DispatcherWrapper) {
    runTest(dispatcherWrapper) {
      flux(dispatcherWrapper.dispatcher) {
        repeat(3) {
          tracedChild("child_$it")
          send(it)
        }
      }.collect {
      }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("child_0")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("child_1")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("child_2")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    )
  }

  private fun tracedChild(opName: String) {
    tracer.spanBuilder(opName).startSpan().end()
  }

  private fun <T> runTest(dispatcherWrapper: DispatcherWrapper, block: suspend CoroutineScope.() -> T): T {
    return runTest(dispatcherWrapper.dispatcher, block)
  }

  private fun <T> runTest(dispatcher: CoroutineDispatcher, block: suspend CoroutineScope.() -> T): T {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope = parentSpan.makeCurrent()
    try {
      return runBlocking(dispatcher, block = block)
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  private suspend fun a(iter: Long) {
    var span = tracer.spanBuilder("a").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
      a2(iter)
    }
    span.end()
  }

  private suspend fun a2(iter: Long) {
    var span = tracer.spanBuilder("a2").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
    }
    span.end()
  }

  private suspend fun b(iter: Long) {
    var span = tracer.spanBuilder("b").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
      b2(iter)
    }
    span.end()
  }

  private suspend fun b2(iter: Long) {
    var span = tracer.spanBuilder("b2").startSpan()
    span.setAttribute("iter", iter)
    withContext(span.asContextElement()) {
      delay(10)
    }
    span.end()
  }

  class DispatchersSource : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> =
      Stream.of(
        // Wrap dispatchers since it seems that ParameterizedTest tries to automatically close
        // Closeable arguments with no way to avoid it.
        arguments(DispatcherWrapper(Dispatchers.Default)),
        arguments(DispatcherWrapper(Dispatchers.IO)),
        arguments(DispatcherWrapper(Dispatchers.Unconfined)),
        arguments(DispatcherWrapper(threadPool.asCoroutineDispatcher())),
        arguments(DispatcherWrapper(singleThread.asCoroutineDispatcher())),
      )
  }

  class DispatcherWrapper(val dispatcher: CoroutineDispatcher) {
    override fun toString(): String = dispatcher.toString()
  }
}
