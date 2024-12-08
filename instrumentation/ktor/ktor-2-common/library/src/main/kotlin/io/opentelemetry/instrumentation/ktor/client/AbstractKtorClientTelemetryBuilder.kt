/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.client

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.ktor.internal.KtorBuilderUtil

abstract class AbstractKtorClientTelemetryBuilder(
  private val instrumentationName: String
) {
  companion object {
    init {
      KtorBuilderUtil.clientBuilderExtractor = { it.clientBuilder }
    }
  }

  internal lateinit var openTelemetry: OpenTelemetry
  protected lateinit var clientBuilder: DefaultHttpClientInstrumenterBuilder<HttpRequestData, HttpResponse>

  fun setOpenTelemetry(openTelemetry: OpenTelemetry) {
    this.openTelemetry = openTelemetry
    this.clientBuilder = DefaultHttpClientInstrumenterBuilder.create(
      instrumentationName,
      openTelemetry,
      KtorHttpClientAttributesGetter
    )
  }

  protected fun getOpenTelemetry(): OpenTelemetry {
    return openTelemetry
  }

  fun capturedRequestHeaders(vararg headers: String) = capturedRequestHeaders(headers.asIterable())

  fun capturedRequestHeaders(headers: Iterable<String>) {
    clientBuilder.setCapturedRequestHeaders(headers.toList())
  }

  fun capturedResponseHeaders(vararg headers: String) = capturedResponseHeaders(headers.asIterable())

  fun capturedResponseHeaders(headers: Iterable<String>) {
    clientBuilder.setCapturedResponseHeaders(headers.toList())
  }

  fun knownMethods(vararg methods: String) = knownMethods(methods.asIterable())

  fun knownMethods(vararg methods: HttpMethod) = knownMethods(methods.asIterable())

  @JvmName("knownMethodsJvm")
  fun knownMethods(methods: Iterable<HttpMethod>) = knownMethods(methods.map { it.value })

  fun knownMethods(methods: Iterable<String>) {
    clientBuilder.setKnownMethods(methods.toSet())
  }

  fun attributeExtractor(extractorBuilder: ExtractorBuilder.() -> Unit = {}) {
    val builder = ExtractorBuilder().apply(extractorBuilder).build()
    this.clientBuilder.addAttributeExtractor(
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
   * Can be used via the unstable method {@link
   * Experimental#setEmitExperimentalHttpClientMetrics(AbstractKtorClientTelemetryBuilder, boolean)}.
   */
  internal fun emitExperimentalHttpClientMetrics() {
    clientBuilder.setEmitExperimentalHttpClientMetrics(true)
  }
}
