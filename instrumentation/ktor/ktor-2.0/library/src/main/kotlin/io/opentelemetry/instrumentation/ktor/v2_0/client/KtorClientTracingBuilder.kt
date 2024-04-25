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

  @Deprecated(
    "Please use method `capturedRequestHeaders`",
    ReplaceWith("capturedRequestHeaders(headers.asIterable())")
  )
  fun setCapturedRequestHeaders(vararg headers: String) = capturedRequestHeaders(headers.asIterable())

  @Deprecated(
    "Please use method `capturedRequestHeaders`",
    ReplaceWith("capturedRequestHeaders(headers)")
  )
  fun setCapturedRequestHeaders(headers: List<String>) = capturedRequestHeaders(headers)

  fun capturedRequestHeaders(vararg headers: String) = capturedRequestHeaders(headers.asIterable())

  fun capturedRequestHeaders(headers: Iterable<String>) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(headers.toList())
  }

  @Deprecated(
    "Please use method `capturedResponseHeaders`",
    ReplaceWith("capturedResponseHeaders(headers.asIterable())")
  )
  fun setCapturedResponseHeaders(vararg headers: String) = capturedResponseHeaders(headers.asIterable())

  @Deprecated(
    "Please use method `capturedResponseHeaders`",
    ReplaceWith("capturedResponseHeaders(headers)")
  )
  fun setCapturedResponseHeaders(headers: List<String>) = capturedResponseHeaders(headers)

  fun capturedResponseHeaders(vararg headers: String) = capturedResponseHeaders(headers.asIterable())

  fun capturedResponseHeaders(headers: Iterable<String>) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(headers.toList())
  }

  @Deprecated(
    "Please use method `knownMethods`",
    ReplaceWith("knownMethods(knownMethods)")
  )
  fun setKnownMethods(knownMethods: Set<String>) = knownMethods(knownMethods)

  fun knownMethods(vararg methods: String) = knownMethods(methods.asIterable())

  fun knownMethods(vararg methods: HttpMethod) = knownMethods(methods.asIterable())

  @JvmName("knownMethodsJvm")
  fun knownMethods(methods: Iterable<HttpMethod>) = knownMethods(methods.map { it.value })

  fun knownMethods(methods: Iterable<String>) {
    methods.toSet().apply {
      httpAttributesExtractorBuilder.setKnownMethods(this)
      httpSpanNameExtractorBuilder.setKnownMethods(this)
    }
  }

  @Deprecated("Please use method `attributeExtractor`")
  fun addAttributesExtractors(vararg extractors: AttributesExtractor<in HttpRequestData, in HttpResponse>) = addAttributesExtractors(extractors.asList())

  @Deprecated("Please use method `attributeExtractor`")
  fun addAttributesExtractors(extractors: Iterable<AttributesExtractor<in HttpRequestData, in HttpResponse>>) {
    extractors.forEach {
      attributeExtractor {
        onStart { it.onStart(attributes, parentContext, request) }
        onEnd { it.onEnd(attributes, parentContext, request, response, error) }
      }
    }
  }

  fun attributeExtractor(extractorBuilder: ExtractorBuilder.() -> Unit = {}) {
    val builder = ExtractorBuilder().apply(extractorBuilder).build()
    additionalExtractors.add(
      object : AttributesExtractor<HttpRequestData, HttpResponse> {
        override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: HttpRequestData) {
          builder.onStart(OnStartData(attributes, parentContext, request))
        }

        override fun onEnd(attributes: AttributesBuilder, context: Context, request: HttpRequestData, response: HttpResponse?, error: Throwable?) {
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
  @Deprecated("Please use method `emitExperimentalHttpClientMetrics`")
  fun setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics: Boolean) {
    if (emitExperimentalHttpClientMetrics) {
      emitExperimentalHttpClientMetrics()
    }
  }

  fun emitExperimentalHttpClientMetrics() {
    emitExperimentalHttpClientMetrics = true
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
