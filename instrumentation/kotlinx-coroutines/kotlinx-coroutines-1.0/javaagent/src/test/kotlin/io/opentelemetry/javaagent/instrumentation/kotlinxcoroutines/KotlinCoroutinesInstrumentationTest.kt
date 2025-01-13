/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.coroutines.CoroutineContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalCoroutinesApi
class KotlinCoroutinesInstrumentationTest {

  companion object {
    val threadPool = Executors.newFixedThreadPool(2)
    val singleThread = Executors.newSingleThreadExecutor()
    val vertx = Vertx.vertx()

    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  @AfterAll
  fun shutdown() {
    threadPool.shutdown()
    singleThread.shutdown()
    vertx.close()
  }

  val tracer = testing.openTelemetry.getTracer("test")

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
      },
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
      },
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
        trace.hasSpansSatisfyingExactlyInAnyOrder(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("future1")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("keptPromise")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("keptPromise2")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("brokenPromise")
              .hasParent(trace.getSpan(0))
          },
        )
      },
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
        },
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
        trace.hasSpansSatisfyingExactlyInAnyOrder(
          {
            it
              .hasName("parent")
              .hasNoParent()
          },
          {
            it
              .hasName("timeout1")
              .hasParent(trace.getSpan(0))
          },
          {
            it
              .hasName("timeout2")
              .hasParent(trace.getSpan(0))
          },
          {
            it
              .hasName("timeout3")
              .hasParent(trace.getSpan(0))
          },
        )
      },
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
    val assertions = mutableListOf<Consumer<TraceAssert>>()
    for (i in 0 until numIters) {
      assertions.add { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("a")
              .hasNoParent()
          },
          {
            it.hasName("a2")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    }
    for (i in 0 until numIters) {
      assertions.add { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("b")
              .hasNoParent()
          },
          {
            it.hasName("b2")
              .hasParent(trace.getSpan(0))
          },
        )
      }
    }

    testing.waitAndAssertSortedTraces(
      orderByRootSpanName("a", "b"),
      *assertions.toTypedArray(),
    )
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
      },
    )
  }

  private val animalKey: ContextKey<String> = ContextKey.named("animal")

  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `context contains expected value`(dispatcher: DispatcherWrapper) {
    runTest(dispatcher) {
      val context1 = Context.current().with(animalKey, "cat")
      runBlocking(context1.asContextElement()) {
        assertThat(Context.current().get(animalKey)).isEqualTo("cat")
        assertThat(coroutineContext.getOpenTelemetryContext().get(animalKey)).isEqualTo("cat")
        tracedChild("nested1")
        withContext(context1.with(animalKey, "dog").asContextElement()) {
          assertThat(Context.current().get(animalKey)).isEqualTo("dog")
          assertThat(coroutineContext.getOpenTelemetryContext().get(animalKey)).isEqualTo("dog")
          tracedChild("nested2")
        }
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
            it.hasName("nested1")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("nested2")
              .hasParent(trace.getSpan(0))
          },
        )
      },
    )
  }

  @Test
  fun `test WithSpan annotation`() {
    runBlocking {
      annotated1()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("a1")
              .hasNoParent()
              .hasAttributesSatisfyingExactly(
                equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, this.javaClass.name),
                equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "annotated1")
              )
          },
          {
            it.hasName("KotlinCoroutinesInstrumentationTest.annotated2")
              .hasParent(trace.getSpan(0))
              .hasAttributesSatisfyingExactly(
                equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, this.javaClass.name),
                equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "annotated2"),
                equalTo(AttributeKey.longKey("byteValue"), 1),
                equalTo(AttributeKey.longKey("intValue"), 4),
                equalTo(AttributeKey.longKey("longValue"), 5),
                equalTo(AttributeKey.longKey("shortValue"), 6),
                equalTo(AttributeKey.doubleKey("doubleValue"), 2.0),
                equalTo(AttributeKey.doubleKey("floatValue"), 3.0),
                equalTo(AttributeKey.booleanKey("booleanValue"), true),
                equalTo(AttributeKey.stringKey("charValue"), "a"),
                equalTo(AttributeKey.stringKey("stringValue"), "test")
              )
          }
        )
      }
    )
  }

  @WithSpan(value = "a1", kind = SpanKind.CLIENT)
  private suspend fun annotated1() {
    delay(10)
    annotated2(1, true, 'a', 2.0, 3.0f, 4, 5, 6, "test")
  }

  @WithSpan
  private suspend fun annotated2(
    @SpanAttribute byteValue: Byte,
    @SpanAttribute booleanValue: Boolean,
    @SpanAttribute charValue: Char,
    @SpanAttribute doubleValue: Double,
    @SpanAttribute floatValue: Float,
    @SpanAttribute intValue: Int,
    @SpanAttribute longValue: Long,
    @SpanAttribute shortValue: Short,
    @SpanAttribute("stringValue") s: String
  ) {
    delay(10)
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/9312
  @Test
  fun `test class with default constructor argument`() {
    runBlocking {
      val classDefaultConstructorArguments = ClazzWithDefaultConstructorArguments()
      classDefaultConstructorArguments.sayHello()
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("ClazzWithDefaultConstructorArguments.sayHello")
              .hasNoParent()
              .hasAttributesSatisfyingExactly(
                equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, ClazzWithDefaultConstructorArguments::class.qualifiedName),
                equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sayHello")
              )
          }
        )
      }
    )
  }

  private fun tracedChild(opName: String) {
    tracer.spanBuilder(opName).startSpan().end()
  }

  private fun <T> runTest(dispatcherWrapper: DispatcherWrapper, block: suspend CoroutineScope.() -> T): T = runTest(dispatcherWrapper.dispatcher, block)

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
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
      // Wrap dispatchers since it seems that ParameterizedTest tries to automatically close
      // Closeable arguments with no way to avoid it.
      arguments(DispatcherWrapper(Dispatchers.Default)),
      arguments(DispatcherWrapper(Dispatchers.IO)),
      arguments(DispatcherWrapper(Dispatchers.Unconfined)),
      arguments(DispatcherWrapper(threadPool.asCoroutineDispatcher())),
      arguments(DispatcherWrapper(singleThread.asCoroutineDispatcher())),
      arguments(DispatcherWrapper(vertx.dispatcher()))
    )
  }

  class DispatcherWrapper(val dispatcher: CoroutineDispatcher) {
    override fun toString(): String = dispatcher.toString()
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7837
  // tests that a custom ThreadContextElement runs after KotlinContextElement that is used for
  // context propagation in coroutines
  @Test
  fun `test custom context element`() {
    val testValue = "test-value"
    val contextKey = ContextKey.named<String>("test-key")
    val scope = Context.current().with(contextKey, "wrong value").makeCurrent()
    scope.use {
      runBlocking {
        val context = Context.current().with(contextKey, testValue)
        withContext(TestContextElement(context)) {
          delay(10)
          val result = Context.current().get(contextKey)
          assertThat(result).isEqualTo(testValue)
        }
      }
    }
  }

  class TestContextElement(private val otelContext: Context) : ThreadContextElement<Scope> {
    companion object Key : CoroutineContext.Key<TestContextElement> {
    }

    override val key: CoroutineContext.Key<TestContextElement>
      get() = Key

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
      oldState.close()
    }

    override fun updateThreadContext(context: CoroutineContext): Scope = otelContext.makeCurrent()
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/11411
  @ParameterizedTest
  @ArgumentsSource(DispatchersSource::class)
  fun `dispatch does not propagate context`(dispatcher: DispatcherWrapper) {
    Assumptions.assumeTrue(dispatcher.dispatcher != Dispatchers.Unconfined)

    runTest(dispatcher) {
      dispatcher.dispatcher.dispatch(coroutineContext) {
        tracer.spanBuilder("dispatched").startSpan().end()
      }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly({
          it.hasName("parent")
            .hasNoParent()
        })
      },
      { trace ->
        trace.hasSpansSatisfyingExactly({
          it.hasName("dispatched")
            .hasNoParent()
        })
      }
    )
  }
}
