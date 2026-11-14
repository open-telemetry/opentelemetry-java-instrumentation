/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.coroutines.resume

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuspendWithSpanTest {

  companion object {
    val threadPool = Executors.newFixedThreadPool(2)
    val singleThread = Executors.newSingleThreadExecutor()

    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  @AfterAll
  fun shutdown() {
    threadPool.shutdown()
    singleThread.shutdown()
  }

  val tracer = testing.openTelemetry.getTracer("test")

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
                equalTo(CODE_FUNCTION_NAME, this.javaClass.name + ".annotated1")
              )
          },
          {
            it.hasName("SuspendWithSpanTest.annotated2")
              .hasParent(trace.getSpan(0))
              .hasAttributesSatisfyingExactly(
                equalTo(CODE_FUNCTION_NAME, this.javaClass.name + ".annotated2"),
                equalTo(AttributeKey.longKey("byteValue"), 1),
                equalTo(AttributeKey.longKey("intValue"), 4),
                equalTo(AttributeKey.longKey("longValue"), 5),
                equalTo(AttributeKey.longKey("shortValue"), 6),
                equalTo(AttributeKey.doubleKey("doubleValue"), 2.0),
                equalTo(AttributeKey.doubleKey("floatValue"), 3.0),
                equalTo(AttributeKey.booleanKey("booleanValue"), true),
                equalTo(AttributeKey.stringKey("charValue"), "a"),
                equalTo(AttributeKey.stringKey("nullableCharValue"), "z"),
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
    annotated2(1, true, 'a', 'z', 2.0, 3.0f, 4, 5, 6, "test")
  }

  @WithSpan
  private suspend fun annotated2(
    @SpanAttribute byteValue: Byte,
    @SpanAttribute booleanValue: Boolean,
    @SpanAttribute charValue: Char,
    @SpanAttribute nullableCharValue: Char?,
    @SpanAttribute doubleValue: Double,
    @SpanAttribute floatValue: Float,
    @SpanAttribute intValue: Int,
    @SpanAttribute longValue: Long,
    @SpanAttribute shortValue: Short,
    @SpanAttribute("stringValue") s: String
  ) {
    delay(10)
  }

  @Test
  fun `test WithSpan annotation on top-level function`() {
    runBlocking {
      topLevelAnnotated(1, 2, "test", "unannotated")
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("TopLevelFunctionsKt.topLevelAnnotated")
              .hasNoParent()
              .hasAttributesSatisfyingExactly(
                equalTo(CODE_FUNCTION_NAME, "io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.TopLevelFunctionsKt.topLevelAnnotated"),
                equalTo(AttributeKey.longKey("intValue"), 1),
                equalTo(AttributeKey.longKey("longValue"), 2),
                equalTo(AttributeKey.stringKey("stringValue"), "test")
              )
          }
        )
      }
    )
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/12837
  @Test
  fun `WithSpan restores effective context after unannotated suspension`() {
    val rootSpan = tracer.spanBuilder("root").startSpan()
    runBlocking(Context.root().asContextElement()) {
      withContext(TestContextElement(rootSpan.storeInContext(Context.root()))) {
        annotatedIntermediate()
        tracer.spanBuilder("after").startSpan().end()
      }
    }
    rootSpan.end()

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("root")
              .hasNoParent()
          },
          {
            it.hasName("intermediate")
              .hasParent(trace.getSpan(0))
          },
          {
            it.hasName("leaf")
              .hasParent(trace.getSpan(1))
          },
          {
            it.hasName("leaf")
              .hasParent(trace.getSpan(1))
          },
          {
            it.hasName("after")
              .hasParent(trace.getSpan(0))
          },
        )
      },
    )
  }

  @WithSpan("intermediate")
  private suspend fun annotatedIntermediate() {
    unannotatedIntermediate()
  }

  private suspend fun unannotatedIntermediate() {
    annotatedLeaf()
    annotatedLeaf()
  }

  @WithSpan("leaf")
  private suspend fun annotatedLeaf() {
    delay(10)
  }

  @Test
  fun `WithSpan preserves coroutine stack frames`() {
    runBlocking {
      stackFrameCaller()
    }
  }

  private suspend fun stackFrameCaller() {
    annotatedStackFrame()
    yield()
  }

  @WithSpan("stack-frame")
  private suspend fun annotatedStackFrame() {
    inspectStackFrame()
  }

  private suspend fun inspectStackFrame() {
    suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
      assertThat(continuation).isInstanceOf(CoroutineStackFrame::class.java)
      val frame = continuation as CoroutineStackFrame
      assertThat(frame.callerFrame).isNotNull()
      assertThat(frame.getStackTraceElement()).isNull()
      Unit
    }
  }

  @Test
  fun `WithSpan ends span on failed completion`() {
    val exception = IllegalStateException("expected")
    val result = runCatching {
      runBlocking {
        annotatedFailure(exception)
      }
    }

    assertThat(result.exceptionOrNull()).isSameAs(exception)
    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("failing")
              .hasNoParent()
              .hasStatus(StatusData.error())
              .hasException(exception)
          },
        )
      },
    )
  }

  @WithSpan("failing")
  private suspend fun annotatedFailure(exception: RuntimeException) {
    unannotatedFailure(exception)
  }

  private suspend fun unannotatedFailure(exception: RuntimeException) {
    delay(10)
    throw exception
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `tail delay preserves dispatcher`(annotated: Boolean) {
    val threads = runBlocking {
      withContext(singleThread.asCoroutineDispatcher()) {
        val callerThread = Thread.currentThread()
        if (annotated) {
          annotatedDelay()
        } else {
          delay(100)
        }
        callerThread to Thread.currentThread()
      }
    }

    if (annotated) {
      testing.waitAndAssertTraces(
        { trace ->
          trace.hasSpansSatisfyingExactly(
            { it.hasName("tail-delay") },
          )
        },
      )
    }
    assertThat(threads.second).isSameAs(threads.first)
  }

  @WithSpan("tail-delay")
  private suspend fun annotatedDelay() = delay(100)

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `tail withContext preserves dispatcher`(annotated: Boolean) {
    val threads = runBlocking {
      withContext(singleThread.asCoroutineDispatcher()) {
        val callerThread = Thread.currentThread()
        if (annotated) {
          annotatedWithContext(threadPool.asCoroutineDispatcher())
        } else {
          withContext(threadPool.asCoroutineDispatcher()) {
            delay(100)
          }
        }
        callerThread to Thread.currentThread()
      }
    }

    if (annotated) {
      testing.waitAndAssertTraces(
        { trace ->
          trace.hasSpansSatisfyingExactly(
            { it.hasName("tail-with-context") },
          )
        },
      )
    }
    assertThat(threads.second).isSameAs(threads.first)
  }

  @WithSpan("tail-with-context")
  private suspend fun annotatedWithContext(dispatcher: CoroutineDispatcher) = withContext(dispatcher) {
    delay(100)
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
                equalTo(CODE_FUNCTION_NAME, ClazzWithDefaultConstructorArguments::class.qualifiedName + ".sayHello")
              )
          }
        )
      }
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `callback suspension preserves dispatcher`(annotated: Boolean) {
    val continuationReady = CompletableFuture<Continuation<Unit>>()
    val threads = runBlocking {
      val result = async(singleThread.asCoroutineDispatcher()) {
        val callerThread = Thread.currentThread()
        if (annotated) {
          annotatedCallback(continuationReady)
        } else {
          callback(continuationReady)
        }
        callerThread to Thread.currentThread()
      }

      val continuation = continuationReady.get(10, TimeUnit.SECONDS)
      // Drain the caller executor so the callback cannot complete before suspension.
      singleThread.submit {}.get(10, TimeUnit.SECONDS)
      threadPool.submit { continuation.resume(Unit) }.get(10, TimeUnit.SECONDS)
      withTimeout(TimeUnit.SECONDS.toMillis(10)) {
        result.await()
      }
    }

    if (annotated) {
      testing.waitAndAssertTraces(
        { trace ->
          trace.hasSpansSatisfyingExactly(
            { it.hasName("callback") },
          )
        },
      )
    }
    assertThat(threads.second).isSameAs(threads.first)
  }

  @WithSpan("callback")
  private suspend fun annotatedCallback(continuationReady: CompletableFuture<Continuation<Unit>>) = callback(continuationReady)

  private suspend fun callback(continuationReady: CompletableFuture<Continuation<Unit>>) = suspendCoroutineUninterceptedOrReturn<Unit> {
    continuationReady.complete(it.intercepted())
    COROUTINE_SUSPENDED
  }

  private class TestContextElement(private val otelContext: Context) : ThreadContextElement<Scope> {
    companion object Key : CoroutineContext.Key<TestContextElement> {
    }

    override val key: CoroutineContext.Key<TestContextElement>
      get() = Key

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
      oldState.close()
    }

    override fun updateThreadContext(context: CoroutineContext): Scope = otelContext.makeCurrent()
  }
}
