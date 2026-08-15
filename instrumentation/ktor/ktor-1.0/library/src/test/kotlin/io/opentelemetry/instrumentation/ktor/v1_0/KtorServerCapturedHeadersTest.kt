/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
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

  private val endpoint = ServerEndpoint("captureHeaders", "captureHeaders", 200, "captured")

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()

    private val REQUEST_HEADER = AttributeKey.stringArrayKey("http.request.header.x-test-request")
    private val RESPONSE_HEADER = AttributeKey.stringArrayKey("http.response.header.x-test-response")
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
      @Suppress("DEPRECATION") // testing the deprecated API
      setCapturedRequestHeaders(listOf("X-Test-Request"))
      @Suppress("DEPRECATION") // testing the deprecated API
      setCapturedResponseHeaders(listOf("X-Test-Response"))
    }

    routing {
      get(endpoint.path) {
        call.response.header("X-Test-Response", call.request.header("X-Test-Request") ?: "")
        call.respondText(endpoint.body)
      }
    }
  }.start()

  override fun stopServer(server: ApplicationEngine) {
    server.stop(0, 10, TimeUnit.SECONDS)
  }

  @Test
  fun testCapturedHeaders() {
    val request = AggregatedHttpRequest.of(
      RequestHeaders.builder(HttpMethod.GET, resolveAddress(endpoint))
        .add("X-Test-Request", "test")
        .build()
    )
    val response = client.execute(request).aggregate().join()
    assertThat(response.status().code()).isEqualTo(endpoint.status)

    testing.waitAndAssertTraces(
      Consumer { trace ->
        trace.hasSpansSatisfyingExactly(
          Consumer { span ->
            span.hasAttribute(REQUEST_HEADER, listOf("test"))
            span.hasAttribute(RESPONSE_HEADER, listOf("test"))
          }
        )
      }
    )
  }
}
