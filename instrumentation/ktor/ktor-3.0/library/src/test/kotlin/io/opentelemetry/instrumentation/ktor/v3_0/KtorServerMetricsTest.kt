/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.instrumentation.ktor.v2_0.common.internal.Experimental
import io.opentelemetry.instrumentation.ktor.v3_0.InstrumentationProperties.INSTRUMENTATION_NAME
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class KtorServerMetricsTest : AbstractHttpServerUsingTest<EmbeddedServer<*, *>>() {
  companion object {
    @JvmStatic
    @RegisterExtension
    val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()
  }

  private val errorDuringSendEndpoint = ServerEndpoint("errorDuringSend", "error-during-send", 500, "")
  private val errorAfterSendEndpoint = ServerEndpoint("errorAfterSend", "error-after-send", 200, "")

  @BeforeAll
  fun setupOptions() {
    startServer()
  }

  @AfterAll
  fun cleanup() {
    cleanupServer()
  }

  override fun getContextPath() = ""

  override fun setupServer(): EmbeddedServer<*, *> = embeddedServer(Netty, port = port) {
    install(KtorServerTelemetry) {
      setOpenTelemetry(testing.openTelemetry)
      Experimental.emitExperimentalTelemetry(this)
    }

    routing {
      get(errorDuringSendEndpoint.path) {
        call.respondBytesWriter {
          throw IllegalArgumentException("exception")
        }
      }
      get(errorAfterSendEndpoint.path) {
        call.respondText(errorAfterSendEndpoint.body, status = HttpStatusCode.fromValue(errorAfterSendEndpoint.status))
        throw IllegalArgumentException("exception")
      }
    }
  }.start()

  override fun stopServer(server: EmbeddedServer<*, *>) {
    server.stop(0, 10, TimeUnit.SECONDS)
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  fun testActiveRequestsMetric(endpoint: ServerEndpoint) {
    val request = AggregatedHttpRequest.of(HttpMethod.valueOf("GET"), resolveAddress(endpoint))
    try {
      client.execute(request).aggregate().join()
    } catch (_: Throwable) {
      // we expect server error
    }

    testing.waitAndAssertMetrics(
      INSTRUMENTATION_NAME,
      "http.server.active_requests"
    ) { metrics ->
      metrics!!.anySatisfy(ThrowingConsumer { metric: MetricData? ->
        OpenTelemetryAssertions.assertThat(metric)
          .hasDescription("Number of active HTTP server requests.")
          .hasUnit("{requests}")
          .hasLongSumSatisfying { sum ->
            sum.hasPointsSatisfying({ point ->
              point.hasValue(0)
                .hasAttributesSatisfying {
                  OpenTelemetryAssertions.equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                  OpenTelemetryAssertions.equalTo(UrlAttributes.URL_PATH, endpoint.path)
                }
            })
          }
      })
    }
  }

  private fun provideArguments(): Stream<Arguments> = Stream.of(
    arguments(errorDuringSendEndpoint),
    arguments(errorAfterSendEndpoint),
  )
}
