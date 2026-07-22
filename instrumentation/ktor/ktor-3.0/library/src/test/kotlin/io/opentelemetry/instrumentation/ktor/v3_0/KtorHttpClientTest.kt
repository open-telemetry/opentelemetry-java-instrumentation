/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.*
import io.ktor.client.statement.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testingExtension = HttpClientInstrumentationExtension.forLibrary()
  }

  override fun HttpClientConfig<*>.installTracing() {
    install(KtorClientTelemetry) {
      setOpenTelemetry(testingExtension.openTelemetry)
      capturedRequestHeaders(TEST_REQUEST_HEADER)
      capturedResponseHeaders(TEST_RESPONSE_HEADER)
    }
  }

  // The library cannot make the client span the ambient context inside the execute block (that
  // needs bytecode instrumentation of HttpStatement.execute, which only the agent has). Instead it
  // exposes the span via the HttpClientCall of the response, using the same span stored on the
  // call attributes that the agent bridge reads.
  @Test
  fun spanAccessibleViaClientCallInExecuteBlock() {
    val attributeKey = AttributeKey.stringKey("HttpClientCall.withClientSpan")
    val attributeValue = UUID.randomUUID().toString()

    val uri = resolveAddress("/success")
    val request = buildRequest("GET", uri, mutableMapOf())

    sendStreamingRequest(request) { response ->
      response.bodyAsText()
      response.call.withClientSpan { setAttribute(attributeKey, attributeValue) }
    }

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { span ->
            span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent()
              .hasAttribute(attributeKey, attributeValue)
          },
          { span -> assertServerSpan(span).hasParent(trace.getSpan(0)) },
        )
      }
    )
  }
}
