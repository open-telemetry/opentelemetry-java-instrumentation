/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class KtorHttpClientStreamingTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing: InstrumentationExtension = LibraryInstrumentationExtension.create()

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private val serverPort = PortUtils.findOpenPort()

    @JvmStatic
    @BeforeAll
    fun setupServer() {
      server = embeddedServer(Netty, port = serverPort) {
        routing {
          get("/success") {
            call.respondText("ok")
          }
        }
      }.start()
    }

    @JvmStatic
    @AfterAll
    fun stopServer() {
      server.stop(0, 10, TimeUnit.SECONDS)
    }
  }

  @Test
  fun `prepareGet execute completes promptly with telemetry installed`() {
    HttpClient(CIO) {
      install(HttpTimeout) {
        requestTimeoutMillis = 30_000
      }
      install(KtorClientTelemetry) {
        setOpenTelemetry(testing.openTelemetry)
      }
    }.use { client ->
      runBlocking {
        withTimeout(5.seconds) {
          repeat(3) {
            client.prepareGet("http://localhost:$serverPort/success").execute { response ->
              response.bodyAsText()
            }
          }
        }
      }
    }

    val maxSpanDuration = 5.seconds
    val traces = testing.waitForTraces(3)
    traces.forEach { trace ->
      trace.forEach { span ->
        val spanDuration = (span.endEpochNanos - span.startEpochNanos).nanoseconds
        assertTrue(spanDuration < maxSpanDuration) {
          "Span duration $spanDuration exceeded $maxSpanDuration, span end may have waited for request timeout"
        }
      }
    }
  }
}
