/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KtorClientHeadersTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing = LibraryInstrumentationExtension.create()

    private val REQUEST_HEADER = AttributeKey.stringArrayKey("http.request.header.x-test-request")
    private val RESPONSE_HEADER = AttributeKey.stringArrayKey("http.response.header.x-test-response")
    private val SECRET_REQUEST_HEADER = AttributeKey.stringArrayKey("http.request.header.x-test-secret")
    private val AUTHORIZATION_HEADER = AttributeKey.stringArrayKey("http.request.header.authorization")
  }

  @Test
  fun `selector patterns capture headers that are not listed by name`() {
    val span = record { telemetryBuilder ->
      telemetryBuilder.requestHeaders(
        IncludeExclude.builder().setIncluded("X-Test-*").setExcluded("*-secret").build()
      )
      telemetryBuilder.responseHeaders(IncludeExclude.builder().setExcluded("Content-*").build())
    }

    assertThat(span.attributes.get(REQUEST_HEADER)).containsExactly("request-value")
    assertThat(span.attributes.get(RESPONSE_HEADER)).containsExactly("response-value")
    // excluded patterns take precedence over included patterns, and header matching is
    // case-insensitive
    assertThat(span.attributes.get(SECRET_REQUEST_HEADER)).isNull()
  }

  @Test
  fun `deprecated captured headers capture headers listed by name`() {
    val span = record { telemetryBuilder ->
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetryBuilder.capturedRequestHeaders("X-Test-Request", "Authorization")
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetryBuilder.capturedResponseHeaders("X-Test-Response")
    }

    assertThat(span.attributes.get(REQUEST_HEADER)).containsExactly("request-value")
    assertThat(span.attributes.get(RESPONSE_HEADER)).containsExactly("response-value")
    // capturing Authorization here is what makes asserting that it is absent in
    // `deprecated captured headers ignore wildcard values` meaningful
    assertThat(span.attributes.get(AUTHORIZATION_HEADER)).containsExactly("secret-value")
  }

  @Test
  fun `deprecated captured headers ignore wildcard values`() {
    val span = record { telemetryBuilder ->
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetryBuilder.capturedRequestHeaders("*")
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetryBuilder.capturedResponseHeaders("*")
    }

    // "*" is dropped while the selector is built, so it captures nothing; the request carries an
    // Authorization header so that treating "*" as a glob pattern would capture it
    assertThat(span.attributes.get(AUTHORIZATION_HEADER)).isNull()
    assertThat(span.attributes.asMap().keys.map { it.key })
      .noneMatch { it.startsWith("http.request.header.") }
      .noneMatch { it.startsWith("http.response.header.") }
  }

  private fun record(configure: (TestKtorClientTelemetryBuilder) -> Unit): SpanData {
    val telemetryBuilder = TestKtorClientTelemetryBuilder()
    telemetryBuilder.setOpenTelemetry(testing.openTelemetry)
    configure(telemetryBuilder)
    val instrumenter = telemetryBuilder.buildInstrumenter()

    lateinit var request: HttpRequestData
    HttpClient(MockEngine) {
      engine {
        addHandler { requestData ->
          request = requestData
          respond("", HttpStatusCode.OK, headersOf("X-Test-Response", "response-value"))
        }
      }
    }.use { client ->
      val response = runBlocking {
        client.get("http://localhost/test") {
          header("X-Test-Request", "request-value")
          header("X-Test-Secret", "secret-value")
          header("Authorization", "secret-value")
        }
      }
      val context = instrumenter.start(Context.root(), request)
      instrumenter.end(context, request, response, null)
    }

    return testing.spans().single()
  }
}

private class TestKtorClientTelemetryBuilder : AbstractKtorClientTelemetryBuilder("test") {

  fun buildInstrumenter(): Instrumenter<HttpRequestData, HttpResponse> = builder.build()
}
