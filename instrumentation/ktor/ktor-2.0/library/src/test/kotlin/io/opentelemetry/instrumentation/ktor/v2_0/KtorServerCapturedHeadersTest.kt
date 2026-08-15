/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class KtorServerCapturedHeadersTest : AbstractHttpServerUsingTest<ApplicationEngine>() {

  private val endpoint = ServerEndpoint.CAPTURE_HEADERS

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()

    private val REQUEST_HEADER = AttributeKey.stringArrayKey("http.request.header.x-test-request")
    private val RESPONSE_HEADER = AttributeKey.stringArrayKey("http.response.header.x-test-response")
    private val SECRET_REQUEST_HEADER = AttributeKey.stringArrayKey("http.request.header.x-test-secret")
  }

  @BeforeAll
  fun setupOptions() {
    startServer()
  }

  @AfterAll
  fun cleanup() {
    cleanupServer()
  }

  override fun getContextPath() = ""

  override fun setupServer(): ApplicationEngine = embeddedServer(Netty, port = port) {
    install(KtorServerTelemetry) {
      setOpenTelemetry(testing.openTelemetry)
      // "*" is included to verify that it is matched as a literal header name instead of as a
      // glob pattern
      @Suppress("DEPRECATION") // testing the deprecated API
      capturedRequestHeaders("X-Test-Request", "*")
      @Suppress("DEPRECATION") // testing the deprecated API
      capturedResponseHeaders(listOf("X-Test-Response"))
    }

    routing {
      get(endpoint.path) {
        call.response.header("X-Test-Response", "response-value")
        call.respondText(endpoint.body)
      }
    }
  }.start()

  override fun stopServer(server: ApplicationEngine) {
    server.stop(0, 10, TimeUnit.SECONDS)
  }

  @Test
  fun testDeprecatedCapturedHeaders() {
    val request = AggregatedHttpRequest.of(
      RequestHeaders.builder(HttpMethod.GET, resolveAddress(endpoint))
        .add("X-Test-Request", "request-value")
        .add("X-Test-Secret", "secret-value")
        .build()
    )
    val response = client.execute(request).aggregate().join()
    assertThat(response.status().code()).isEqualTo(endpoint.status)

    testing.waitAndAssertTraces(
      Consumer { trace ->
        trace.hasSpansSatisfyingExactly(
          Consumer { span ->
            span.hasAttribute(REQUEST_HEADER, listOf("request-value"))
            span.hasAttribute(RESPONSE_HEADER, listOf("response-value"))
          }
        )
      }
    )

    // the deprecated functions take exact header names, so "*" looks for a header literally named
    // "*" instead of capturing every header
    assertThat(testing.spans().single().attributes.get(SECRET_REQUEST_HEADER)).isNull()
  }
}
