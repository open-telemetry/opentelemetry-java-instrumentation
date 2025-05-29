/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.concurrent.Executors
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalCoroutinesApi
class KotlinCoroutines13InstrumentationTest {

  companion object {
    val threadPool = Executors.newFixedThreadPool(2)
    val singleThread = Executors.newSingleThreadExecutor()
  }

  @AfterAll
  fun shutdown() {
    threadPool.shutdown()
    singleThread.shutdown()
  }

  @RegisterExtension
  val testing = AgentInstrumentationExtension.create()

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
        trace.hasSpansSatisfyingExactlyInAnyOrder(
          {
            it.hasName("parent")
              .hasNoParent()
          },
          {
            it.hasName("produce_0")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("consume_0")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("produce_1")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("consume_1")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("produce_2")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("consume_2")
              .hasParent(trace.getSpan(0))
          },
        )
      },
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
      },
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
      },
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

  class DispatchersSource : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
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
