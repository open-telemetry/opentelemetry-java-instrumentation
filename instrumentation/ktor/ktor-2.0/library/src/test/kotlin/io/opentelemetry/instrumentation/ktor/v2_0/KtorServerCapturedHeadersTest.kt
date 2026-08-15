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
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit

class KtorServerCapturedHeadersTest : AbstractHttpServerUsingTest<ApplicationEngine>() {

  private val endpoint = ServerEndpoint.CAPTURE_HEADERS

  private var configureHeaders: (KtorServerTelemetryBuilder) -> Unit = {}

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()

    private val REQUEST_HEADER = AttributeKey.stringArrayKey("http.request.header.x-test-request")
    private val RESPONSE_HEADER = AttributeKey.stringArrayKey("http.response.header.x-test-response")
    private val AUTHORIZATION_HEADER = AttributeKey.stringArrayKey("http.request.header.authorization")
  }

  override fun getContextPath() = ""

  override fun setupServer(): ApplicationEngine = embeddedServer(Netty, port = port) {
    install(KtorServerTelemetry) {
      setOpenTelemetry(testing.openTelemetry)
      configureHeaders(this)
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
  fun testCapturedHeadersListedByName() {
    val attributes = captureAttributes { telemetry ->
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetry.capturedRequestHeaders("X-Test-Request", "Authorization")
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetry.capturedResponseHeaders("X-Test-Response")
    }

    assertThat(attributes.get(REQUEST_HEADER)).containsExactly("request-value")
    assertThat(attributes.get(RESPONSE_HEADER)).containsExactly("response-value")
    // capturing Authorization here is what makes asserting that it is absent in
    // testDeprecatedFunctionsIgnoreWildcardValues meaningful
    assertThat(attributes.get(AUTHORIZATION_HEADER)).containsExactly("secret-value")
  }

  @Test
  fun testDeprecatedFunctionsIgnoreWildcardValues() {
    val attributes = captureAttributes { telemetry ->
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetry.capturedRequestHeaders("X-Test-Request", "*")
      @Suppress("DEPRECATION") // testing the deprecated API
      telemetry.capturedResponseHeaders("*")
    }

    // "*" is dropped while the selector is built, so it never widens what is captured; the request
    // carries an Authorization header so that treating "*" as a glob pattern would capture it
    assertThat(attributes.get(AUTHORIZATION_HEADER)).isNull()
    assertThat(headerAttributeKeys(attributes, "http.request.header."))
      .containsExactly("http.request.header.x-test-request")
    assertThat(headerAttributeKeys(attributes, "http.response.header.")).isEmpty()
  }

  private fun captureAttributes(configure: (KtorServerTelemetryBuilder) -> Unit): Attributes {
    configureHeaders = configure
    startServer()
    try {
      val request = AggregatedHttpRequest.of(
        RequestHeaders.builder(HttpMethod.GET, resolveAddress(endpoint))
          .add("X-Test-Request", "request-value")
          .add("Authorization", "secret-value")
          .build()
      )
      val response = client.execute(request).aggregate().join()
      assertThat(response.status().code()).isEqualTo(endpoint.status)

      testing.waitForTraces(1)
      return testing.spans().single().attributes
    } finally {
      cleanupServer()
    }
  }

  private fun headerAttributeKeys(attributes: Attributes, prefix: String): List<String> = attributes.asMap().keys.map { it.key }.filter { it.startsWith(prefix) }
}
