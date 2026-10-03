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
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit

class KtorServerHeaderSelectorTest : AbstractHttpServerUsingTest<ApplicationEngine>() {

  private val endpoint = ServerEndpoint.CAPTURE_HEADERS

  private var configureHeaders: (KtorServerTelemetry.Configuration) -> Unit = {}

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
  fun capturesHeadersConfiguredByName() {
    val attributes = captureAttributes { telemetry ->
      telemetry.requestHeaders(IncludeExclude.builder().setIncluded("X-Test-Request", "Authorization").build())
      telemetry.responseHeaders(IncludeExclude.builder().setIncluded("X-Test-Response").build())
    }

    assertThat(attributes.get(REQUEST_HEADER)).containsExactly("request-value")
    assertThat(attributes.get(RESPONSE_HEADER)).containsExactly("response-value")
    assertThat(attributes.get(AUTHORIZATION_HEADER)).containsExactly("secret-value")
  }

  @Test
  fun capturesOnlyConfiguredHeaders() {
    val attributes = captureAttributes { telemetry ->
      telemetry.requestHeaders(IncludeExclude.builder().setIncluded("X-Test-Request").build())
    }

    assertThat(attributes.get(AUTHORIZATION_HEADER)).isNull()
    assertThat(headerAttributeKeys(attributes, "http.request.header."))
      .containsExactly("http.request.header.x-test-request")
    assertThat(headerAttributeKeys(attributes, "http.response.header.")).isEmpty()
  }

  @Test
  fun customizesTelemetry() {
    val attributes = captureAttributes(ServerEndpoint.NOT_FOUND) { telemetry ->
      telemetry.knownMethods(listOf("CUSTOM"))
      telemetry.spanNameExtractor { _ -> SpanNameExtractor { "custom" } }
      telemetry.spanStatusExtractor { _ ->
        SpanStatusExtractor { status, _, _, _ -> status.setStatus(StatusCode.ERROR) }
      }
      telemetry.attributesExtractor(object : AttributesExtractor<ApplicationRequest, ApplicationResponse> {
        override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: ApplicationRequest) {
          attributes.put("custom.start", "start-value")
        }

        override fun onEnd(attributes: AttributesBuilder, context: Context, request: ApplicationRequest, response: ApplicationResponse?, error: Throwable?) {
          attributes.put("custom.end", "end-value")
        }
      })
    }

    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("_OTHER")
    assertThat(attributes.get(AttributeKey.stringKey("custom.start"))).isEqualTo("start-value")
    assertThat(attributes.get(AttributeKey.stringKey("custom.end"))).isEqualTo("end-value")
    val span = testing.spans().single()
    assertThat(span.name).isEqualTo("custom")
    assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
  }

  private fun captureAttributes(requestEndpoint: ServerEndpoint = endpoint, configure: (KtorServerTelemetry.Configuration) -> Unit): Attributes {
    configureHeaders = configure
    startServer()
    try {
      val request = AggregatedHttpRequest.of(
        RequestHeaders.builder(HttpMethod.GET, resolveAddress(requestEndpoint))
          .add("X-Test-Request", "request-value")
          .add("Authorization", "secret-value")
          .build()
      )
      val response = client.execute(request).aggregate().join()
      assertThat(response.status().code()).isEqualTo(requestEndpoint.status)

      testing.waitForTraces(1)
      return testing.spans().single().attributes
    } finally {
      cleanupServer()
    }
  }

  private fun headerAttributeKeys(attributes: Attributes, prefix: String): List<String> = attributes.asMap().keys.map { it.key }.filter { it.startsWith(prefix) }
}
