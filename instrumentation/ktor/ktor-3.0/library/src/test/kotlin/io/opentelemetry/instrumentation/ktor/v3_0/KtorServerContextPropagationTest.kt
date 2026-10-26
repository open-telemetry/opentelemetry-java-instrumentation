/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class KtorServerContextPropagationTest {
  private data class ObservedSpan(val traceId: String, val spanId: String, val recording: Boolean)

  @Test
  fun eachRequestContinuesItsOwnTraceparent() = verifyEachRequestContinuesItsOwnTraceparent(dispatcherSwitch = false)

  @Test
  fun eachRequestContinuesItsOwnTraceparentAfterDispatcherSwitch() = verifyEachRequestContinuesItsOwnTraceparent(dispatcherSwitch = true)

  private fun verifyEachRequestContinuesItsOwnTraceparent(dispatcherSwitch: Boolean) {
    System.getProperty("otel.test.ktor.regularJarVersion")?.let { version ->
      assertThat(Application::class.java.protectionDomain.codeSource.location.path)
        .contains("ktor-server-core-jvm-$version.jar")
    }
    if (System.getProperty("otel.test.ktor.fatJar") == "true") {
      val expectedVersion = if (System.getProperty("otel.test.ktor.genericManifestVersion") == "true") "1.0.0" else null
      assertThat(Application::class.java.`package`.implementationVersion).isEqualTo(expectedVersion)
      assertThat(Application::class.java.protectionDomain.codeSource.location.path).contains("application-all-ktor")
      val pipelineClass = Class.forName("io.ktor.util.pipeline.SuspendFunctionGun", false, Application::class.java.classLoader)
      assertThat(pipelineClass.`package`.implementationVersion).isEqualTo(expectedVersion)
      assertThat(pipelineClass.protectionDomain.codeSource.location.path).contains("application-all-ktor")
    }

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

    val port = ServerSocket(0).use { it.localPort }
    val server = embeddedServer(Netty, configure = {
      connector { this.port = port }
      callGroupSize = 1 // Sequential requests share one call thread.
    }) {
      install(KtorServerTelemetry) { openTelemetry(openTelemetry) }
      install(CallId) {
        header(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
      }
      routing {
        get("/") {
          delay(5)
          if (dispatcherSwitch) {
            withContext(Dispatchers.IO) { delay(5) }
          }
          val span = Span.current()
          call.respondText("${span.spanContext.traceId} ${span.spanContext.spanId} ${span.isRecording}")
        }
      }
    }.start(wait = false)
    val sent = (1..5).map { "%032x".format(it) }
    fun request(traceId: String?): ObservedSpan {
      val connection = URI("http://localhost:$port/").toURL().openConnection() as HttpURLConnection
      try {
        if (traceId != null) {
          connection.setRequestProperty("traceparent", "00-$traceId-00f067aa0ba902b7-01")
        }
        assertThat(connection.responseCode).isEqualTo(200)
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

    assertThat(seen.map { it.traceId })
      .describedAs("handler trace IDs")
      .isEqualTo(sent)
    assertThat(seen.map { it.spanId }.distinct())
      .describedAs("distinct handler span IDs")
      .hasSize(sent.size)
    assertThat(seen.map { it.recording })
      .describedAs("handler spans are still recording")
      .containsOnly(true)
    assertThat(completedSpans.take(sent.size).map { it.traceId })
      .describedAs("completed span trace IDs for requests with parents")
      .isEqualTo(sent)
    assertThat(completedSpans.map { it.kind })
      .describedAs("completed SERVER spans")
      .containsOnly(SpanKind.SERVER)
      .hasSize(seen.size + 1)
    assertThat(completedSpans.map { it.spanId })
      .describedAs("completed spans match handler spans")
      .isEqualTo(seen.map { it.spanId } + noParentSeen.spanId)
    assertThat(noParentSeen.traceId)
      .describedAs("request without traceparent starts a new trace")
      .isNotIn(sent)
    assertThat(completedSpans.last().traceId)
      .describedAs("unparented request's completed trace ID")
      .isEqualTo(noParentSeen.traceId)
  }
}
