/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.context.Context
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

  fun capturedRequestHeaders(vararg headers: String) {
    capturedRequestHeaders(headers.asIterable())
  }

  fun capturedRequestHeaders(headers: Iterable<String>) {
    setCapturedRequestHeaders(headers.toList())
  }

  fun setCapturedResponseHeaders(vararg headers: String) = setCapturedResponseHeaders(headers.asList())

  fun setCapturedResponseHeaders(headers: List<String>) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(headers)
  }

  fun capturedResponseHeaders(vararg headers: String) {
    capturedResponseHeaders(headers.asIterable())
  }

  fun capturedResponseHeaders(headers: Iterable<String>) {
    setCapturedResponseHeaders(headers.toList())
  }

  fun setKnownMethods(knownMethods: Set<String>) {
    httpAttributesExtractorBuilder.setKnownMethods(knownMethods)
    httpSpanNameExtractorBuilder.setKnownMethods(knownMethods)
  }

  fun knownMethods(vararg methods: String) {
    setKnownMethods(methods.toSet())
  }

  fun knownMethods(methods: Iterable<String>) {
    setKnownMethods(methods.toSet())
  }

  fun knownMethods(vararg methods: HttpMethod) {
    knownMethods(methods.asIterable())
  }

  @JvmName("knownMethodsJvm")
  fun knownMethods(methods: Iterable<HttpMethod>) {
    setKnownMethods(methods.map { it.value }.toSet())
  }

  fun addAttributesExtractors(vararg extractors: AttributesExtractor<in HttpRequestData, in HttpResponse>) = addAttributesExtractors(extractors.asList())

  fun addAttributesExtractors(extractors: Iterable<AttributesExtractor<in HttpRequestData, in HttpResponse>>) {
    additionalExtractors += extractors
  }

  fun attributeExtractor(
    extractorBuilder: ExtractorBuilder.() -> Unit = {}
  ) {
    val builder = ExtractorBuilder().apply(extractorBuilder).build()
    addAttributesExtractors(
      object : AttributesExtractor<HttpRequestData, HttpResponse> {
        override fun onStart(
          attributes: AttributesBuilder,
          parentContext: Context,
          request: HttpRequestData
        ) {
          builder.onStart(OnStartData(attributes, parentContext, request))
        }

        override fun onEnd(
          attributes: AttributesBuilder,
          context: Context,
          request: HttpRequestData,
          response: HttpResponse?,
          error: Throwable?
        ) {
          builder.onEnd(OnEndData(attributes, context, request, response, error))
        }
      }
    )
  }

  class ExtractorBuilder {
    private var onStart: OnStartData.() -> Unit = {}
    private var onEnd: OnEndData.() -> Unit = {}

    fun onStart(block: OnStartData.() -> Unit) {
      onStart = block
    }

    fun onEnd(block: OnEndData.() -> Unit) {
      onEnd = block
    }

    internal fun build(): Extractor {
      return Extractor(onStart, onEnd)
    }
  }

  internal class Extractor(val onStart: OnStartData.() -> Unit, val onEnd: OnEndData.() -> Unit)

  data class OnStartData(
    val attributes: AttributesBuilder,
    val parentContext: Context,
    val request: HttpRequestData
  )

  data class OnEndData(
    val attributes: AttributesBuilder,
    val parentContext: Context,
    val request: HttpRequestData,
    val response: HttpResponse?,
    val error: Throwable?
  )

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics `true` if the experimental HTTP client metrics are to be emitted.
   */
  fun setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics: Boolean) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics
  }

  fun emitExperimentalHttpClientMetrics() {
    setEmitExperimentalHttpClientMetrics(true)
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
