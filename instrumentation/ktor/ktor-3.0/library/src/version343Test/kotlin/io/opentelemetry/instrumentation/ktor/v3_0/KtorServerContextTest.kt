/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.*
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.TraceAssert
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.function.Consumer

class KtorServerContextTest {
  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing = LibraryInstrumentationExtension.create()
  }

  @ParameterizedTest
  @CsvSource("false,false", "true,false", "false,true", "true,true")
  fun `requests retain their own context after suspension`(extraContext: Boolean, dispatcherHop: Boolean) = runBlocking {
    val serverPort = PortUtils.findOpenPort()
    val server = embeddedServer(Netty, configure = {
      connector { port = serverPort }
      callGroupSize = 1
    }) {
      install(KtorServerTelemetry) { openTelemetry(testing.openTelemetry) }
      if (extraContext) {
        // Like CallId, another interceptor can continue the pipeline under an extra context element.
        intercept(ApplicationCallPipeline.Setup) {
          withContext(CoroutineName("request")) { proceed() }
        }
      }
      routing {
        get("/context") {
          delay(5)
          if (dispatcherHop) withContext(Dispatchers.IO) { delay(5) }
          val span = Span.current()
          call.respondText("${span.spanContext.traceId},${span.spanContext.spanId},${span.isRecording}")
        }
      }
    }.start(wait = false)
    val client = HttpClient(CIO)
    val parents = mutableListOf<Span>()
    val responses = try {
      (1..4).map {
        val parent = testing.openTelemetry.getTracer("test").spanBuilder("parent").setNoParent().startSpan()
        parents.add(parent)
        try {
          val response = client.get("http://localhost:$serverPort/context") {
            header("traceparent", "00-${parent.spanContext.traceId}-${parent.spanContext.spanId}-01")
          }
          assertThat(response.status.value).isEqualTo(200)
          response.bodyAsText().split(',')
        } finally {
          parent.end()
        }
      }
    } finally {
      client.close()
      server.stop(0, 1000)
    }

    assertThat(responses.map { it[0] }).containsExactlyElementsOf(parents.map { it.spanContext.traceId })
    assertThat(responses.map { it[1] }).doesNotHaveDuplicates()
    assertThat(responses.map { it[2] }).containsExactly("true", "true", "true", "true")
    testing.waitAndAssertTraces(parents.mapIndexed { index, parent ->
      Consumer<TraceAssert> { trace ->
        trace.hasSpansSatisfyingExactly(
          { span -> span.hasName("parent").hasNoParent().hasSpanId(parent.spanContext.spanId) },
          { span ->
            span.hasName("GET /context")
              .hasKind(SpanKind.SERVER)
              .hasTraceId(parent.spanContext.traceId)
              .hasSpanId(responses[index][1])
              .hasParentSpanId(parent.spanContext.spanId)
          }
        )
      }
    })
  }
}
