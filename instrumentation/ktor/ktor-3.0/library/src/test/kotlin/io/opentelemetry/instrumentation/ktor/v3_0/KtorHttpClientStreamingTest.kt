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
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

class KtorHttpClientStreamingTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing: InstrumentationExtension = LibraryInstrumentationExtension.create()

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private var serverPort: Int = 0

    @JvmStatic
    @BeforeAll
    fun setupServer() {
      serverPort = ServerSocket(0).use { it.localPort }
      server = embeddedServer(Netty, port = serverPort) {
        routing {
          get("/success") {
            call.respondText("ok")
          }
        }
      }.start(wait = false)
      Thread.sleep(1000)
    }

    @JvmStatic
    @AfterAll
    fun stopServer() {
      server.stop(1000, 2000)
    }
  }

  @Test
  fun `prepareGet execute completes promptly with telemetry installed`() {
    val client = HttpClient(CIO) {
      install(HttpTimeout) {
        requestTimeoutMillis = 30_000
      }
      install(KtorClientTelemetry) {
        setOpenTelemetry(testing.openTelemetry)
      }
    }

    runBlocking {
      withTimeout(5.seconds) {
        repeat(3) {
          client.prepareGet("http://localhost:$serverPort/success").execute { response ->
            response.bodyAsText()
          }
        }
      }
    }

    client.close()
  }
}
