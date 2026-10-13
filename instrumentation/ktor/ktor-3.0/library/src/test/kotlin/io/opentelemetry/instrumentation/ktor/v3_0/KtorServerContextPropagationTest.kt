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
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class KtorServerContextPropagationTest {
  private data class ObservedSpan(val traceId: String, val spanId: String, val recording: Boolean)

  @Test
  fun eachRequestContinuesItsOwnTraceparent() {
    val completedSpans = CopyOnWriteArrayList<SpanData>()
    val processor = object : SpanProcessor {
      override fun isStartRequired() = false
      override fun isEndRequired() = true
      override fun onStart(parentContext: Context, span: ReadWriteSpan) = Unit
      override fun onEnd(span: ReadableSpan) {
        completedSpans.add(span.toSpanData())
      }
    }
    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(processor).build())
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .build()

    val server = embeddedServer(Netty, configure = {
      connector { port = 0 }
      callGroupSize = 1 // Sequential requests share one call thread.
    }) {
      install(KtorServerTelemetry) { openTelemetry(openTelemetry) }
      install(CallId) {
        header(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
      }
      routing {
        get("/") {
          delay(5) // Suspension alone is enough; no dispatcher hop is needed.
          val span = Span.current()
          call.respondText("${span.spanContext.traceId} ${span.spanContext.spanId} ${span.isRecording}")
        }
      }
    }.start(wait = false)
    val sent = (1..5).map { "%032x".format(it) }
    val port = runBlocking { server.engine.resolvedConnectors().single().port }
    fun request(traceId: String?): ObservedSpan {
      val connection = URI("http://localhost:$port/").toURL().openConnection() as HttpURLConnection
      try {
        if (traceId != null) {
          connection.setRequestProperty("traceparent", "00-$traceId-00f067aa0ba902b7-01")
        }
        assertEquals(200, connection.responseCode)
        val (observedTraceId, spanId, recording) = connection.inputStream.bufferedReader().use { it.readText() }.split(' ')
        return ObservedSpan(observedTraceId, spanId, recording.toBooleanStrict())
      } finally {
        connection.disconnect()
      }
    }
    val seen: List<ObservedSpan>
    val noParentSeen: ObservedSpan
    try {
      seen = sent.map { traceId -> request(traceId) }
      // A request without a remote parent must not inherit the preceding request's trace.
      noParentSeen = request(null)
    } finally {
      try {
        server.stop(0, 1000)
      } finally {
        openTelemetry.close()
      }
    }

    assertAll(
      "Each request must have its own active SERVER span",
      { assertEquals(sent, seen.map { it.traceId }, "Handler trace IDs") },
      { assertEquals(sent.size, seen.map { it.spanId }.distinct().size, "Distinct handler span IDs") },
      { assertEquals(List(sent.size) { true }, seen.map { it.recording }, "Handler spans are still recording") },
      { assertEquals(sent, completedSpans.take(sent.size).map { it.traceId }, "Completed span trace IDs for requests with parents") },
      { assertEquals(List(seen.size + 1) { SpanKind.SERVER }, completedSpans.map { it.kind }, "Completed SERVER spans") },
      { assertEquals(seen.map { it.spanId } + noParentSeen.spanId, completedSpans.map { it.spanId }, "Completed spans match handler spans") },
      { assertEquals(false, noParentSeen.traceId in sent, "Request without traceparent starts a new trace") },
      { assertEquals(noParentSeen.traceId, completedSpans.last().traceId, "Unparented request's completed trace ID") },
    )
  }
}
