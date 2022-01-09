/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import com.google.inject.AbstractModule
import com.google.inject.Provides
import groovy.transform.CompileStatic
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.ratpack.OpenTelemetryServerHandler
import io.opentelemetry.instrumentation.ratpack.RatpackFunctionalTest
import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.instrumentation.ratpack.client.RatpackHttpTracing
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import ratpack.exec.ExecInitializer
import ratpack.exec.ExecInterceptor
import ratpack.guice.Guice
import ratpack.http.client.HttpClient
import ratpack.server.RatpackServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_ROUTE
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_TARGET

class RatpackServerApplicationTest extends Specification {

  def app = new RatpackFunctionalTest(RatpackApp)

  def "add span on handlers"() {
    expect:
    app.test { httpClient -> "hi-foo" == httpClient.get("foo").body.text }

    new PollingConditions().eventually {
      def spanData = app.spanExporter.finishedSpanItems.find { it.name == "/foo" }
      def attributes = spanData.attributes.asMap()

      spanData.kind == SpanKind.SERVER
      attributes[HTTP_ROUTE] == "/foo"
      attributes[HTTP_TARGET] == "/foo"
      attributes[HTTP_METHOD] == "GET"
      attributes[HTTP_STATUS_CODE] == 200L
    }
  }

  def "propagate trace to http calls"() {
    expect:
    app.test { httpClient -> "hi-bar" == httpClient.get("bar").body.text }

    new PollingConditions().eventually {
      def spanData = app.spanExporter.finishedSpanItems.find { it.name == "/bar" }
      def spanDataClient = app.spanExporter.finishedSpanItems.find { it.name == "HTTP GET" }
      def attributes = spanData.attributes.asMap()

      spanData.traceId == spanDataClient.traceId

      spanData.kind == SpanKind.SERVER
      attributes[HTTP_ROUTE] == "/bar"
      attributes[HTTP_TARGET] == "/bar"
      attributes[HTTP_METHOD] == "GET"
      attributes[HTTP_STATUS_CODE] == 200L

      spanDataClient.kind == SpanKind.CLIENT
      def attributesClient = spanDataClient.attributes.asMap()
      attributesClient[HTTP_ROUTE] == "/other"
      attributesClient[HTTP_METHOD] == "GET"
      attributesClient[HTTP_STATUS_CODE] == 200L
    }
  }

  def "ignore handlers before OpenTelemetryServerHandler"() {
    expect:
    app.test { httpClient -> "ignored" == httpClient.get("ignore").body.text }

    new PollingConditions(initialDelay: 0.1, timeout: 0.3).eventually {
      !app.spanExporter.finishedSpanItems.any { it.name == "/ignore" }
    }
  }
}


@CompileStatic
class OpenTelemetryModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SpanExporter).toInstance(InMemorySpanExporter.create())
  }

  @Singleton
  @Provides
  RatpackTracing ratpackTracing(OpenTelemetry openTelemetry) {
    return RatpackTracing.create(openTelemetry)
  }

  @Singleton
  @Provides
  OpenTelemetryServerHandler ratpackServerHandler(RatpackTracing ratpackTracing) {
    return ratpackTracing.getOpenTelemetryServerHandler()
  }

  @Singleton
  @Provides
  ExecInterceptor ratpackExecInterceptor(RatpackTracing ratpackTracing) {
    return ratpackTracing.getOpenTelemetryExecInterceptor()
  }

  @Provides
  @Singleton
  OpenTelemetry providesOpenTelemetry(SpanExporter spanExporter) {
    def tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
      .build()
    return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
  }

  @Singleton
  @Provides
  RatpackHttpTracing ratpackHttpTracing(OpenTelemetry openTelemetry) {
    return RatpackHttpTracing.create(openTelemetry)
  }

  @Singleton
  @Provides
  HttpClient instrumentedHttpClient(RatpackHttpTracing ratpackHttpTracing) {
    return ratpackHttpTracing.instrumentedHttpClient(HttpClient.of {})
  }

  @Singleton
  @Provides
  ExecInitializer ratpackExecInitializer(RatpackHttpTracing ratpackTracing) {
    return ratpackTracing.getOpenTelemetryExecInitializer()
  }

}

class RatpackApp {

  static void main(String... args) {
    def other = RatpackServer.start { server ->
      server.handlers { chain ->
        chain.get("other") { ctx -> ctx.render("hi-other") }
      }
    }

    RatpackServer.start { server ->
      server
        .registry(Guice.registry { b -> b.module(OpenTelemetryModule) })
        .handlers { chain ->
          chain
            .get("ignore") { ctx -> ctx.render("ignored") }
            .all(OpenTelemetryServerHandler)
            .get("foo") { ctx -> ctx.render("hi-foo") }
            .get("bar") { ctx ->
              def instrumentedHttpClient = ctx.get(HttpClient)
              instrumentedHttpClient.get(new URI("http://${other.bindHost}:${other.bindPort}/other"))
                .then { ctx.render("hi-bar") }
            }
        }
    }
  }
}
