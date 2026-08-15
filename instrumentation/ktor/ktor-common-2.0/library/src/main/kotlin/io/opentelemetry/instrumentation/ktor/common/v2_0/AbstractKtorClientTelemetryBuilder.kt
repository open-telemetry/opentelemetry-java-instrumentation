/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor
import io.opentelemetry.instrumentation.ktor.common.v2_0.internal.KtorBuilderUtil
import java.util.function.UnaryOperator

abstract class AbstractKtorClientTelemetryBuilder(
  private val instrumentationName: String
) {
  companion object {
    init {
      KtorBuilderUtil.clientBuilderExtractor = { it.builder }
    }
  }

  internal lateinit var openTelemetry: OpenTelemetry
  protected lateinit var builder: DefaultHttpClientInstrumenterBuilder<HttpRequestData, HttpResponse>

  internal fun builder(): DefaultHttpClientInstrumenterBuilder<HttpRequestData, HttpResponse> = builder

  fun setOpenTelemetry(openTelemetry: OpenTelemetry) {
    this.openTelemetry = openTelemetry
    this.builder = DefaultHttpClientInstrumenterBuilder.create(
      instrumentationName,
      openTelemetry,
      KtorHttpClientAttributesGetter
    )
  }

  protected fun getOpenTelemetry(): OpenTelemetry = openTelemetry

  /**
   * Configures which HTTP request headers are captured as span attributes.
   *
   * Header values are captured under the `http.request.header.<key>` attribute key, where the
   * `<key>` part is the lowercase header name.
   *
   * Header names are matched case-insensitively, since HTTP header names are case-insensitive. `?`
   * matches one character and `*` matches any number of characters, including none. Excluded
   * patterns take precedence over included patterns. A selector with no included patterns captures
   * every header that is not excluded, and an empty selector captures no headers. No request
   * headers are captured when this is not configured.
   */
  fun requestHeaders(requestHeaders: IncludeExclude) {
    builder.setRequestHeaders(requestHeaders)
  }

  /**
   * Configures which HTTP request headers are captured as span attributes, by exact header name.
   *
   * The header names are matched literally. Names containing `*` or `?` are ignored, since
   * this setting never supported wildcards. Use [requestHeaders] to match names by pattern.
   */
  // may be removed in the next minor release
  @Deprecated(
    "Use requestHeaders(IncludeExclude) instead, which matches glob patterns rather than " +
      "literal header names. May be removed in the next minor release."
  )
  fun capturedRequestHeaders(vararg headers: String) {
    builder.setCapturedRequestHeaders(headers.toList())
  }

  /**
   * Configures which HTTP request headers are captured as span attributes, by exact header name.
   *
   * The header names are matched literally. Names containing `*` or `?` are ignored, since
   * this setting never supported wildcards. Use [requestHeaders] to match names by pattern.
   */
  // may be removed in the next minor release
  @Deprecated(
    "Use requestHeaders(IncludeExclude) instead, which matches glob patterns rather than " +
      "literal header names. May be removed in the next minor release."
  )
  fun capturedRequestHeaders(headers: Iterable<String>) {
    builder.setCapturedRequestHeaders(headers.toList())
  }

  /**
   * Configures which HTTP response headers are captured as span attributes.
   *
   * Header values are captured under the `http.response.header.<key>` attribute key, where the
   * `<key>` part is the lowercase header name.
   *
   * Header names are matched case-insensitively, since HTTP header names are case-insensitive. `?`
   * matches one character and `*` matches any number of characters, including none. Excluded
   * patterns take precedence over included patterns. A selector with no included patterns captures
   * every header that is not excluded, and an empty selector captures no headers. No response
   * headers are captured when this is not configured.
   */
  fun responseHeaders(responseHeaders: IncludeExclude) {
    builder.setResponseHeaders(responseHeaders)
  }

  /**
   * Configures which HTTP response headers are captured as span attributes, by exact header name.
   *
   * The header names are matched literally. Names containing `*` or `?` are ignored, since
   * this setting never supported wildcards. Use [responseHeaders] to match names by pattern.
   */
  // may be removed in the next minor release
  @Deprecated(
    "Use responseHeaders(IncludeExclude) instead, which matches glob patterns rather than " +
      "literal header names. May be removed in the next minor release."
  )
  fun capturedResponseHeaders(vararg headers: String) {
    builder.setCapturedResponseHeaders(headers.toList())
  }

  /**
   * Configures which HTTP response headers are captured as span attributes, by exact header name.
   *
   * The header names are matched literally. Names containing `*` or `?` are ignored, since
   * this setting never supported wildcards. Use [responseHeaders] to match names by pattern.
   */
  // may be removed in the next minor release
  @Deprecated(
    "Use responseHeaders(IncludeExclude) instead, which matches glob patterns rather than " +
      "literal header names. May be removed in the next minor release."
  )
  fun capturedResponseHeaders(headers: Iterable<String>) {
    builder.setCapturedResponseHeaders(headers.toList())
  }

  fun knownMethods(vararg methods: String) = knownMethods(methods.asIterable())

  fun knownMethods(vararg methods: HttpMethod) = knownMethods(methods.asIterable())

  @JvmName("knownMethodsJvm")
  fun knownMethods(methods: Iterable<HttpMethod>) = knownMethods(methods.map { it.value })

  fun knownMethods(methods: Iterable<String>) {
    builder.setKnownMethods(methods.toSet())
  }

  fun attributesExtractor(extractorBuilder: ExtractorBuilder.() -> Unit = {}) {
    val builder = ExtractorBuilder().apply(extractorBuilder).build()
    this.builder.addAttributesExtractor(object : AttributesExtractor<HttpRequestData, HttpResponse> {
      override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: HttpRequestData) {
        builder.onStart(OnStartData(attributes, parentContext, request))
      }

      override fun onEnd(attributes: AttributesBuilder, context: Context, request: HttpRequestData, response: HttpResponse?, error: Throwable?) {
        builder.onEnd(OnEndData(attributes, context, request, response, error))
      }
    })
  }

  fun spanNameExtractor(spanNameExtractor: UnaryOperator<SpanNameExtractor<HttpRequestData>>) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractor)
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

    internal fun build(): Extractor = Extractor(onStart, onEnd)
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
}
