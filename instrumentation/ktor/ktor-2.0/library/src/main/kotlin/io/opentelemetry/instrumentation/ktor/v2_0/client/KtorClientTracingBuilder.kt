/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor
import io.opentelemetry.instrumentation.ktor.v2_0.InstrumentationProperties.INSTRUMENTATION_NAME

class KtorClientTracingBuilder {

  private var openTelemetry: OpenTelemetry? = null
  private val additionalExtractors = mutableListOf<AttributesExtractor<in HttpRequestData, in HttpResponse>>()
  private val httpAttributesExtractorBuilder = HttpClientAttributesExtractor.builder(KtorHttpClientAttributesGetter)
  private val httpSpanNameExtractorBuilder = HttpSpanNameExtractor.builder(KtorHttpClientAttributesGetter)
  private var emitExperimentalHttpClientMetrics = false

  fun setOpenTelemetry(openTelemetry: OpenTelemetry) {
    this.openTelemetry = openTelemetry
  }

  fun setCapturedRequestHeaders(vararg headers: String) = setCapturedRequestHeaders(headers.asList())

  fun setCapturedRequestHeaders(headers: List<String>) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(headers)
  }

  fun setCapturedResponseHeaders(vararg headers: String) = setCapturedResponseHeaders(headers.asList())

  fun setCapturedResponseHeaders(headers: List<String>) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(headers)
  }

  fun setKnownMethods(knownMethods: Set<String>) {
    httpAttributesExtractorBuilder.setKnownMethods(knownMethods)
    httpSpanNameExtractorBuilder.setKnownMethods(knownMethods)
  }

  fun addAttributesExtractors(vararg extractors: AttributesExtractor<in HttpRequestData, in HttpResponse>) = addAttributesExtractors(extractors.asList())

  fun addAttributesExtractors(extractors: Iterable<AttributesExtractor<in HttpRequestData, in HttpResponse>>) {
    additionalExtractors += extractors
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics `true` if the experimental HTTP client metrics are to be emitted.
   */
  fun setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics: Boolean) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics
  }

  internal fun build(): KtorClientTracing {
    val initializedOpenTelemetry = openTelemetry
      ?: throw IllegalArgumentException("OpenTelemetry must be set")

    val instrumenterBuilder = Instrumenter.builder<HttpRequestData, HttpResponse>(
      initializedOpenTelemetry,
      INSTRUMENTATION_NAME,
      httpSpanNameExtractorBuilder.build()
    )
      .setSpanStatusExtractor(HttpSpanStatusExtractor.create(KtorHttpClientAttributesGetter))
      .addAttributesExtractor(httpAttributesExtractorBuilder.build())
      .addAttributesExtractors(additionalExtractors)
      .addOperationMetrics(HttpClientMetrics.get())

    if (emitExperimentalHttpClientMetrics) {
      instrumenterBuilder
        .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(KtorHttpClientAttributesGetter))
        .addOperationMetrics(HttpClientExperimentalMetrics.get())
    }

    val instrumenter = instrumenterBuilder
      .buildInstrumenter(alwaysClient())

    return KtorClientTracing(
      instrumenter = instrumenter,
      propagators = initializedOpenTelemetry.propagators,
    )
  }
}
