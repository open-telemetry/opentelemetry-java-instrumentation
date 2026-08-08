/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.*
import io.ktor.client.statement.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension
import kotlinx.coroutines.currentCoroutineContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testingExtension = HttpClientInstrumentationExtension.forAgent()
  }

  override fun HttpClientConfig<*>.installTracing() {
  }

  // The agent wraps HttpStatement.execute so the client span is active as the ambient context
  // inside the execute block, reachable via Span.current() and the coroutine context. The library
  // cannot do this; it exposes the span via HttpClientCall instead (see the library test).
  @Test
  fun spanAmbientInExecuteBlock() {
    val spanCurrentAttributeKey = AttributeKey.stringKey("Span.current()")
    val spanCurrentAttributeValue = UUID.randomUUID().toString()

    val coroutineContextAttributeKey = AttributeKey.stringKey("currentCoroutineContext()")
    val coroutineContextAttributeValue = UUID.randomUUID().toString()

    // The agent bridges the shaded Context stored on the call, so the library's HttpClientCall
    // accessor also works under the agent (see KtorClientExtensionsInstrumentation).
    val clientCallAttributeKey = AttributeKey.stringKey("HttpClientCall.withClientSpan")
    val clientCallAttributeValue = UUID.randomUUID().toString()

    val uri = resolveAddress("/success")
    val request = buildRequest("GET", uri, mutableMapOf())

    sendStreamingRequest(request) { response ->
      response.bodyAsText()
      Span.current().setAttribute(spanCurrentAttributeKey, spanCurrentAttributeValue)
      Span.fromContext(currentCoroutineContext().getOpenTelemetryContext()).setAttribute(coroutineContextAttributeKey, coroutineContextAttributeValue)
      response.call.withClientSpan { setAttribute(clientCallAttributeKey, clientCallAttributeValue) }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { span ->
            span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent()
              .hasAttribute(spanCurrentAttributeKey, spanCurrentAttributeValue)
              .hasAttribute(coroutineContextAttributeKey, coroutineContextAttributeValue)
              .hasAttribute(clientCallAttributeKey, clientCallAttributeValue)
          },
          { span -> assertServerSpan(span).hasParent(trace.getSpan(0)) },
        )
      }
    )
  }
}
