/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.coroutines.EmptyCoroutineContext

class CoroutinesSelectorTest {

  @RegisterExtension
  val testing = AgentInstrumentationExtension.create()

  @Test
  fun `Flow selector controls completion at collection`() {
    val result = tracedFlow()
    val now = Clock.systemUTC().instant()
    val collectionStart = TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano
    runBlocking { result.count() }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          {
            it.hasName("CoroutinesSelectorTest.tracedFlow")
              .satisfies(Consumer { span ->
                if (System.getProperty("test.flow.enabled", "true").toBoolean()) {
                  assertThat(span.endEpochNanos).isGreaterThan(collectionStart)
                } else {
                  assertThat(span.endEpochNanos).isLessThan(collectionStart)
                }
              })
          }
        )
      }
    )
  }

  @Test
  fun `core selector installs coroutine context element`() {
    val key = ContextKey.named<String>("selector-test")
    val context = Context.current().with(key, "value").makeCurrent().use {
      CoroutineScope(Dispatchers.Default).newCoroutineContext(EmptyCoroutineContext)
    }
    assertThat(
      context.fold(false) { found, element ->
        found || element.javaClass.simpleName == "KotlinContextElement"
      }
    )
      .isEqualTo(System.getProperty("test.core.enabled", "true").toBoolean())
  }

  @WithSpan
  fun tracedFlow(): Flow<Int> = flow { emit(1) }
}
