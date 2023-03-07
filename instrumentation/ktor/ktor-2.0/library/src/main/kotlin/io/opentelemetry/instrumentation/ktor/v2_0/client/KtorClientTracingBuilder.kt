/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor
import io.opentelemetry.instrumentation.ktor.v2_0.InstrumentationProperties.INSTRUMENTATION_NAME

class KtorClientTracingBuilder {

  private var openTelemetry: OpenTelemetry? = null
  private val additionalExtractors = mutableListOf<AttributesExtractor<in HttpRequestData, in HttpResponse>>()
  private val httpAttributesExtractorBuilder = HttpClientAttributesExtractor.builder(
    KtorHttpClientAttributesGetter,
    KtorNetClientAttributesGetter,
  )

  fun setOpenTelemetry(openTelemetry: OpenTelemetry) {
    this.openTelemetry = openTelemetry
  }

  fun setCapturedRequestHeaders(vararg headers: String) =
    setCapturedRequestHeaders(headers.asList())

  fun setCapturedRequestHeaders(headers: List<String>) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(headers)
  }

  fun setCapturedResponseHeaders(vararg headers: String) =
    setCapturedResponseHeaders(headers.asList())

  fun setCapturedResponseHeaders(headers: List<String>) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(headers)
  }

  fun addAttributesExtractors(vararg extractors: AttributesExtractor<in HttpRequestData, in HttpResponse>) =
    addAttributesExtractors(extractors.asList())

  fun addAttributesExtractors(extractors: Iterable<AttributesExtractor<in HttpRequestData, in HttpResponse>>) {
    additionalExtractors += extractors
  }

  internal fun build(): KtorClientTracing {
    val initializedOpenTelemetry = openTelemetry
      ?: throw IllegalArgumentException("OpenTelemetry must be set")

    val instrumenterBuilder = Instrumenter.builder<HttpRequestData, HttpResponse>(
      initializedOpenTelemetry,
      INSTRUMENTATION_NAME,
      HttpSpanNameExtractor.create(KtorHttpClientAttributesGetter),
    )

    val instrumenter = instrumenterBuilder
      .setSpanStatusExtractor(HttpSpanStatusExtractor.create(KtorHttpClientAttributesGetter))
      .addAttributesExtractor(httpAttributesExtractorBuilder.build())
      .addAttributesExtractors(additionalExtractors)
      .addOperationMetrics(HttpClientMetrics.get())
      .buildInstrumenter(alwaysClient())

    return KtorClientTracing(
      instrumenter = instrumenter,
      propagators = initializedOpenTelemetry.propagators,
    )
  }
}
