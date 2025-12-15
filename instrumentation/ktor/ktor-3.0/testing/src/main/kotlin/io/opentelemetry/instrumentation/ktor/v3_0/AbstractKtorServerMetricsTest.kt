/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpMethod
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/**
 * Abstract test class for testing experimental HTTP server metrics (http.server.active_requests).
 * This is a regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/15303
 *
 * Subclasses should use @EnabledIfSystemProperty to ensure tests only run when
 * experimental flag is enabled.
 */
abstract class AbstractKtorServerMetricsTest : AbstractHttpServerUsingTest<EmbeddedServer<*, *>>() {

  private val errorDuringSendEndpoint = ServerEndpoint("errorDuringSend", "error-during-send", 500, "")
  private val errorAfterSendEndpoint = ServerEndpoint("errorAfterSend", "error-after-send", 200, "")

  abstract fun serverInstall(application: Application)
  abstract fun instrumentationName(): String

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
    serverInstall(this)

    routing {
      get(errorDuringSendEndpoint.path) {
        call.respondBytesWriter {
          throw IllegalArgumentException("exception")
        }
      }
      get(errorAfterSendEndpoint.path) {
        call.respondText(
          errorAfterSendEndpoint.body,
          status = HttpStatusCode.fromValue(errorAfterSendEndpoint.status)
        )
        throw IllegalArgumentException("exception")
      }
    }
  }.start()

  override fun stopServer(server: EmbeddedServer<*, *>) {
    server.stop(0, 10, TimeUnit.SECONDS)
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/15303
  // verify that active requests are counted correctly when there is a send error
  @ParameterizedTest
  @MethodSource("provideArguments")
  fun testActiveRequestsMetric(endpoint: ServerEndpoint) {
    val request = AggregatedHttpRequest.of(HttpMethod.valueOf("GET"), resolveAddress(endpoint))
    try {
      client.execute(request).aggregate().join()
    } catch (_: Throwable) {
      // we expect server error
    }

    testing().waitAndAssertMetrics(
      instrumentationName(),
      "http.server.active_requests"
    ) { metrics ->
      metrics!!.anySatisfy(ThrowingConsumer { metric: MetricData? ->
        assertThat(metric)
          .hasDescription("Number of active HTTP server requests.")
          .hasUnit("{requests}")
          .hasLongSumSatisfying { sum ->
            sum.hasPointsSatisfying({ point ->
              point.hasValue(0)
                .hasAttributesSatisfying {
                  equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                  equalTo(UrlAttributes.URL_PATH, endpoint.path)
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
