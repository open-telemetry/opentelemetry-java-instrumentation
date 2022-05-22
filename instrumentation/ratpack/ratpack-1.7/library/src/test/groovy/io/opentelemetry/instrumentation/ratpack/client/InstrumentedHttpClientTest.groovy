/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.instrumentation.ratpack.RatpackTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import ratpack.exec.Promise
import ratpack.func.Action
import ratpack.guice.Guice
import ratpack.http.client.HttpClient
import ratpack.test.embed.EmbeddedApp
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_ROUTE
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE

class InstrumentedHttpClientTest extends Specification {

  def spanExporter = InMemorySpanExporter.create()
  def tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
    .build()

  def openTelemetry = OpenTelemetrySdk.builder()
    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    .setTracerProvider(tracerProvider).build()

  RatpackTelemetry telemetry = RatpackTelemetry.create(openTelemetry)

  def cleanup() {
    spanExporter.reset()
  }

  def "propagate trace with http calls"() {
    expect:
    def otherApp = EmbeddedApp.of { spec ->
      spec.registry(
        Guice.registry { bindings ->
          telemetry.configureServerRegistry(bindings)
        }
      )
      spec.handlers {
        it.get("bar") { ctx -> ctx.render("foo") }
      }
    }

    def app = EmbeddedApp.of { spec ->
      spec.registry(
        Guice.registry { bindings ->
          telemetry.configureServerRegistry(bindings)
          bindings.bindInstance(HttpClient, telemetry.instrumentHttpClient(HttpClient.of(Action.noop())))
        }
      )

      spec.handlers { chain ->
        chain.get("foo") { ctx ->
          HttpClient instrumentedHttpClient = ctx.get(HttpClient)
          instrumentedHttpClient.get(new URI("${otherApp.address}bar"))
            .then { ctx.render("bar") }
        }
      }
    }

    app.test { httpClient ->
      "bar" == httpClient.get("foo").body.text
    }

    new PollingConditions().eventually {
      def spanData = spanExporter.finishedSpanItems.find { it.name == "/foo" }
      def spanClientData = spanExporter.finishedSpanItems.find { it.name == "HTTP GET" && it.kind == SpanKind.CLIENT }
      def spanDataApi = spanExporter.finishedSpanItems.find { it.name == "/bar" && it.kind == SpanKind.SERVER }

      spanData.traceId == spanClientData.traceId
      spanData.traceId == spanDataApi.traceId

      spanData.kind == SpanKind.SERVER
      spanClientData.kind == SpanKind.CLIENT
      def atts = spanClientData.attributes.asMap()
      atts[HTTP_ROUTE] == "/bar"
      atts[HTTP_METHOD] == "GET"
      atts[HTTP_STATUS_CODE] == 200L

      def attributes = spanData.attributes.asMap()
      attributes[HTTP_ROUTE] == "/foo"
      attributes[SemanticAttributes.HTTP_TARGET] == "/foo"
      attributes[HTTP_METHOD] == "GET"
      attributes[HTTP_STATUS_CODE] == 200L

      def attsApi = spanDataApi.attributes.asMap()
      attsApi[HTTP_ROUTE] == "/bar"
      attsApi[SemanticAttributes.HTTP_TARGET] == "/bar"
      attsApi[HTTP_METHOD] == "GET"
      attsApi[HTTP_STATUS_CODE] == 200L
    }
  }

  def "add spans for multiple concurrent client calls"() {
    expect:
    def latch = new CountDownLatch(2)

    def otherApp = EmbeddedApp.of { spec ->
      spec.handlers { chain ->
        chain.get("foo") { ctx -> ctx.render("bar") }
        chain.get("bar") { ctx -> ctx.render("foo") }
      }
    }

    def app = EmbeddedApp.of { spec ->
      spec.registry(
        Guice.registry { bindings ->
          telemetry.configureServerRegistry(bindings)
          bindings.bindInstance(HttpClient, telemetry.instrumentHttpClient(HttpClient.of(Action.noop())))
        }
      )

      spec.handlers { chain ->
        chain.get("path-name") { ctx ->
          ctx.render("hello")
          def instrumentedHttpClient = ctx.get(HttpClient)
          instrumentedHttpClient.get(new URI("${otherApp.address}foo")).then { latch.countDown() }
          instrumentedHttpClient.get(new URI("${otherApp.address}bar")).then { latch.countDown() }
        }
      }
    }

    app.test { httpClient ->
      "hello" == httpClient.get("path-name").body.text
      latch.await(1, TimeUnit.SECONDS)
    }

    new PollingConditions().eventually {
      spanExporter.finishedSpanItems.size() == 3
      def spanData = spanExporter.finishedSpanItems.find { spanData -> spanData.name == "/path-name" }
      def spanClientData1 = spanExporter.finishedSpanItems.find { s -> s.name == "HTTP GET" && s.attributes.asMap()[HTTP_ROUTE] == "/foo" }
      def spanClientData2 = spanExporter.finishedSpanItems.find { s -> s.name == "HTTP GET" && s.attributes.asMap()[HTTP_ROUTE] == "/bar" }

      spanData.traceId == spanClientData1.traceId
      spanData.traceId == spanClientData2.traceId

      spanData.kind == SpanKind.SERVER

      spanClientData1.kind == SpanKind.CLIENT
      def atts = spanClientData1.attributes.asMap()
      atts[HTTP_ROUTE] == "/foo"
      atts[HTTP_METHOD] == "GET"
      atts[HTTP_STATUS_CODE] == 200L

      spanClientData2.kind == SpanKind.CLIENT
      def atts2 = spanClientData2.attributes.asMap()
      atts2[HTTP_ROUTE] == "/bar"
      atts2[HTTP_METHOD] == "GET"
      atts2[HTTP_STATUS_CODE] == 200L

      def attributes = spanData.attributes.asMap()
      attributes[HTTP_ROUTE] == "/path-name"
      attributes[SemanticAttributes.HTTP_TARGET] == "/path-name"
      attributes[HTTP_METHOD] == "GET"
      attributes[HTTP_STATUS_CODE] == 200L
    }
  }

  def "handling exception errors in http client"() {
    expect:
    def otherApp = EmbeddedApp.of { spec ->
      spec.handlers {
        it.get("foo") { ctx ->
          Promise.value("bar").defer(Duration.ofSeconds(1L))
            .then { ctx.render("bar") }
        }
      }
    }

    def app = EmbeddedApp.of { spec ->
      spec.registry(
        Guice.registry { bindings ->
          telemetry.configureServerRegistry(bindings)
          bindings.bindInstance(HttpClient, telemetry.instrumentHttpClient(
            HttpClient.of { s -> s.readTimeout(Duration.ofMillis(10)) })
          )
        }
      )

      spec.handlers { chain ->
        chain.get("path-name") { ctx ->
          def instrumentedHttpClient = ctx.get(HttpClient)
          instrumentedHttpClient.get(new URI("${otherApp.address}foo"))
            .onError { ctx.render("error") }
            .then { ctx.render("hello") }
        }
      }
    }

    app.test { httpClient -> "error" == httpClient.get("path-name").body.text
    }

    new PollingConditions().eventually {
      def spanData = spanExporter.finishedSpanItems.find { it.name == "/path-name" }
      def spanClientData = spanExporter.finishedSpanItems.find { it.name == "HTTP GET" }

      spanData.traceId == spanClientData.traceId

      spanData.kind == SpanKind.SERVER
      spanClientData.kind == SpanKind.CLIENT
      def atts = spanClientData.attributes.asMap()
      atts[HTTP_ROUTE] == "/foo"
      atts[HTTP_METHOD] == "GET"
      atts[HTTP_STATUS_CODE] == null
      spanClientData.status.statusCode == StatusCode.ERROR
      spanClientData.events.first().name == "exception"

      def attributes = spanData.attributes.asMap()
      attributes[HTTP_ROUTE] == "/path-name"
      attributes[SemanticAttributes.HTTP_TARGET] == "/path-name"
      attributes[HTTP_METHOD] == "GET"
      attributes[HTTP_STATUS_CODE] == 200L
    }
  }
}
