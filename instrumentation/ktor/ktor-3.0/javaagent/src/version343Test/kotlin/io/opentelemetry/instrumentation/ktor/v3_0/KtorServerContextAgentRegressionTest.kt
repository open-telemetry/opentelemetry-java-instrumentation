/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.function.Consumer

/**
 * Reproduces #20496 with the Java agent rather than library instrumentation. The test task
 * disables Netty instrumentation so a Netty server span cannot hide a leaked Ktor request context.
 */
class KtorServerContextAgentRegressionTest {
  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  private data class Observed(val traceId: String, val spanId: String, val recording: Boolean)

  @Test
  fun callIdSuspensionPreservesEachRequestContext() {
    val port = PortUtils.findOpenPort()
    val server = embeddedServer(Netty, configure = {
      connector { this.port = port }
      callGroupSize = 1 // Requests reuse a call thread, making a leaked context deterministic.
    }) {
      // The Java agent installs Ktor telemetry; do not install it explicitly.
      install(CallId) {
        header(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
      }
      routing {
        get("/call-id") {
          delay(5)
          val span = Span.current()
          call.respondText("${span.spanContext.traceId},${span.spanContext.spanId},${span.isRecording}")
        }
      }
    }

    val parents = mutableListOf<Span>()
    val observed = try {
      server.start(wait = false)
      (1..5).map {
        val parent = testing.openTelemetry.getTracer("test").spanBuilder("parent").setNoParent().startSpan()
        parents.add(parent)
        try {
          val connection = URI("http://localhost:$port/call-id").toURL().openConnection() as HttpURLConnection
          try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty(
              "traceparent",
              "00-${parent.spanContext.traceId}-${parent.spanContext.spanId}-01"
            )
            assertThat(connection.responseCode).isEqualTo(200)
            val fields = connection.inputStream.bufferedReader().use { it.readText().split(',') }
            assertThat(fields).hasSize(3)
            Observed(fields[0], fields[1], fields[2].toBooleanStrict())
          } finally {
            connection.disconnect()
          }
        } finally {
          parent.end()
        }
      }
    } finally {
      server.stop(0, 1000)
    }

    assertThat(observed.map { it.traceId }).containsExactlyElementsOf(parents.map { it.spanContext.traceId })
    assertThat(observed.map { it.spanId }).doesNotHaveDuplicates()
    assertThat(observed.map { it.recording }).containsOnly(true)

    testing.waitAndAssertTraces(parents.mapIndexed { i, parent ->
      Consumer<TraceAssert> { trace ->
        trace.hasSpansSatisfyingExactly(
          { span -> span.hasName("parent").hasNoParent().hasSpanId(parent.spanContext.spanId) },
          { span ->
            span.hasName("GET /call-id")
              .hasKind(SpanKind.SERVER)
              .hasTraceId(parent.spanContext.traceId)
              .hasSpanId(observed[i].spanId)
              .hasParentSpanId(parent.spanContext.spanId)
          }
        )
      }
    })
  }
}
