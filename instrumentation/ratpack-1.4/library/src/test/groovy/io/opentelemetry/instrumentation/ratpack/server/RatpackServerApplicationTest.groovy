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
import io.opentelemetry.instrumentation.ratpack.OpenTelemetryExecInterceptor
import io.opentelemetry.instrumentation.ratpack.OpenTelemetryServerHandler
import io.opentelemetry.instrumentation.ratpack.RatpackFunctionalTest
import io.opentelemetry.instrumentation.ratpack.RatpackTracing
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import ratpack.guice.Guice
import ratpack.server.RatpackServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class RatpackServerApplicationTest extends Specification {

  def app = new RatpackFunctionalTest(RatpackApp)

  def "add span on handlers"() {
    expect:
    app.test { httpClient -> "hi-foo" == httpClient.get("foo").body.text }

    new PollingConditions().eventually {
      def spanData = app.spanExporter.finishedSpanItems.find { it.name == "/foo" }
      def attributes = spanData.attributes.asMap()

      spanData.kind == SpanKind.SERVER
      attributes[SemanticAttributes.HTTP_ROUTE] == "/foo"
      attributes[SemanticAttributes.HTTP_TARGET] == "/foo"
      attributes[SemanticAttributes.HTTP_METHOD] == "GET"
      attributes[SemanticAttributes.HTTP_STATUS_CODE] == 200L
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
  OpenTelemetryServerHandler ratpackServerHandler(OpenTelemetry openTelemetry) {
    return RatpackTracing.create(openTelemetry).serverHandler
  }

  @Provides
  @Singleton
  OpenTelemetry providesOpenTelemetry(SpanExporter spanExporter) {
    def tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
      .build()
    return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
  }
}

@CompileStatic
class RatpackApp {
  static void main(String... args) {
    RatpackServer.start { server ->
      server
        .registry(
          Guice.registry { bindings ->
            bindings
              .module(OpenTelemetryModule)
              .bind(OpenTelemetryExecInterceptor)
          }
        )
        .handlers { chain ->
          chain
            .get("ignore") { ctx -> ctx.render("ignored") }
            .all(OpenTelemetryServerHandler)
            .get("foo") { ctx -> ctx.render("hi-foo") }
        }
    }
  }
}

