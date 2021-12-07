/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import ratpack.exec.Blocking
import ratpack.registry.Registry
import ratpack.test.embed.EmbeddedApp
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class RatpackServerTest extends Specification {

  def spanExporter = InMemorySpanExporter.create()
  def tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
    .build()

  def openTelemetry = OpenTelemetrySdk.builder()
    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    .setTracerProvider(tracerProvider).build()

  def ratpackTracing = RatpackTracing.create(openTelemetry)

  def cleanup() {
    spanExporter.reset()
  }

  def "add span on handlers"() {
    given:
    def app = EmbeddedApp.of { spec ->
      spec.registry { Registry.of { ratpackTracing.configureServerRegistry(it) } }
      spec.handlers { chain ->
        chain.get("foo") { ctx -> ctx.render("hi-foo") }
      }
    }

    when:
    app.test { httpClient -> "hi-foo" == httpClient.get("foo").body.text }

    then:
    new PollingConditions().eventually {
      def spanData = spanExporter.finishedSpanItems.find { it.name == "/foo" }
      def attributes = spanData.attributes.asMap()

      spanData.kind == SpanKind.SERVER
      attributes[SemanticAttributes.HTTP_ROUTE] == "/foo"
      attributes[SemanticAttributes.HTTP_TARGET] == "/foo"
      attributes[SemanticAttributes.HTTP_METHOD] == "GET"
      attributes[SemanticAttributes.HTTP_STATUS_CODE] == 200L
    }
  }

  def "propagate trace with instrumented async operations"() {
    expect:
    def app = EmbeddedApp.of { spec ->
      spec.registry { Registry.of { ratpackTracing.configureServerRegistry(it) } }
      spec.handlers { chain ->
        chain.get("foo") { ctx ->
          ctx.render("hi-foo")
          Blocking.op {
            def span = openTelemetry.getTracer("any-tracer").spanBuilder("a-span").startSpan()
            span.makeCurrent().withCloseable {
              span.addEvent("an-event")
              span.end()
            }
          }.then()
        }
      }
    }

    app.test { httpClient ->
      "hi-foo" == httpClient.get("foo").body.text

      new PollingConditions().eventually {
        def spanData = spanExporter.finishedSpanItems.find { it.name == "/foo" }
        def spanDataChild = spanExporter.finishedSpanItems.find { it.name == "a-span" }

        spanData.kind == SpanKind.SERVER
        spanData.traceId == spanDataChild.traceId
        spanDataChild.parentSpanId == spanData.spanId
        spanDataChild.events.any { it.name == "an-event" }

        def attributes = spanData.attributes.asMap()
        attributes[SemanticAttributes.HTTP_ROUTE] == "/foo"
        attributes[SemanticAttributes.HTTP_TARGET] == "/foo"
        attributes[SemanticAttributes.HTTP_METHOD] == "GET"
        attributes[SemanticAttributes.HTTP_STATUS_CODE] == 200L
      }
    }
  }

  def "propagate trace with instrumented async concurrent operations"() {
    expect:
    def app = EmbeddedApp.of { spec ->
      spec.registry { Registry.of { ratpackTracing.configureServerRegistry(it) } }
      spec.handlers { chain ->
        chain.get("bar") { ctx ->
          ctx.render("hi-bar")
          Blocking.op {
            def span = openTelemetry.getTracer("any-tracer").spanBuilder("another-span").startSpan()
            span.makeCurrent().withCloseable {
              span.addEvent("an-event")
              span.end()
            }
          }.then()
        }
        chain.get("foo") { ctx ->
          ctx.render("hi-foo")
          Blocking.op {
            def span = openTelemetry.getTracer("any-tracer").spanBuilder("a-span").startSpan()
            span.makeCurrent().withCloseable {
              span.addEvent("an-event")
              span.end()
            }
          }.then()
        }
      }
    }

    app.test { httpClient ->
      "hi-foo" == httpClient.get("foo").body.text
      "hi-bar" == httpClient.get("bar").body.text
      new PollingConditions().eventually {
        def spanData = spanExporter.finishedSpanItems.find { it.name == "/foo" }
        def spanDataChild = spanExporter.finishedSpanItems.find { it.name == "a-span" }

        def spanData2 = spanExporter.finishedSpanItems.find { it.name == "/bar" }
        def spanDataChild2 = spanExporter.finishedSpanItems.find { it.name == "another-span" }

        spanData.kind == SpanKind.SERVER
        spanData.traceId == spanDataChild.traceId
        spanDataChild.parentSpanId == spanData.spanId
        spanDataChild.events.any { it.name == "an-event" }

        spanData2.kind == SpanKind.SERVER
        spanData2.traceId == spanDataChild2.traceId
        spanDataChild2.parentSpanId == spanData2.spanId
        spanDataChild2.events.any { it.name == "an-event" }

        def attributes = spanData.attributes.asMap()
        attributes[SemanticAttributes.HTTP_ROUTE] == "/foo"
        attributes[SemanticAttributes.HTTP_TARGET] == "/foo"
        attributes[SemanticAttributes.HTTP_METHOD] == "GET"
        attributes[SemanticAttributes.HTTP_STATUS_CODE] == 200L
      }
    }
  }
}
