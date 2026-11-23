/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class KtorServerExternalJobTest {
  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing = AgentInstrumentationExtension.create()
  }

  @Test
  fun `requests retain their own context after awaiting an external job`() {
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val externalScope = CoroutineScope(dispatcher)
    // Create these jobs outside any request context, as in the regression from #18373.
    val jobs = List(4) {
      externalScope.launch(start = CoroutineStart.LAZY) { delay(50) }
    }
    val requestIndex = AtomicInteger()
    val serverPort = PortUtils.findOpenPort()
    // CIO avoids a Netty server span masking a loss of the Ktor request context.
    val server = embeddedServer(CIO, port = serverPort) {
      // The Java agent installs the instrumentation. Do not install KtorServerTelemetry here.
      routing {
        get("/external-job") {
          val before = Span.current().spanContext
          jobs[requestIndex.getAndIncrement()].join()
          val after = Span.current()
          val childTraceId = externalScope.async { Span.current().spanContext.traceId }.await()
          call.respondText("${before.spanId},${after.spanContext.traceId},${after.spanContext.spanId},${after.isRecording},$childTraceId")
        }
      }
    }
    val parents = mutableListOf<Span>()
    val responses = try {
      server.start(wait = false)
      jobs.map {
        val parent = testing.openTelemetry.getTracer("test").spanBuilder("parent").setNoParent().startSpan()
        parents.add(parent)
        val connection = URI("http://localhost:$serverPort/external-job").toURL().openConnection() as HttpURLConnection
        try {
          connection.connectTimeout = 10000
          connection.readTimeout = 10000
          connection.setRequestProperty("traceparent", "00-${parent.spanContext.traceId}-${parent.spanContext.spanId}-01")
          assertThat(connection.responseCode).isEqualTo(200)
          connection.inputStream.bufferedReader().use { reader -> reader.readText().split(',') }
        } finally {
          connection.disconnect()
          parent.end()
        }
      }
    } finally {
      server.stop(0, 1000)
      externalScope.cancel()
      dispatcher.close()
    }

    assertThat(responses.map { it[1] }).containsExactlyElementsOf(parents.map { it.spanContext.traceId })
    assertThat(responses.map { it[2] }).doesNotHaveDuplicates()
    responses.forEachIndexed { index, response ->
      assertThat(response[2]).isEqualTo(response[0])
      assertThat(response[3]).isEqualTo("true")
      assertThat(response[4]).isEqualTo(parents[index].spanContext.traceId)
    }
    testing.waitAndAssertTraces(parents.mapIndexed { index, parent ->
      Consumer<TraceAssert> { trace ->
        trace.hasSpansSatisfyingExactly(
          { span -> span.hasName("parent").hasNoParent().hasSpanId(parent.spanContext.spanId) },
          { span ->
            span.hasName("GET /external-job")
              .hasKind(SpanKind.SERVER)
              .hasTraceId(parent.spanContext.traceId)
              .hasSpanId(responses[index][2])
              .hasParentSpanId(parent.spanContext.spanId)
          }
        )
      }
    })
  }
}
