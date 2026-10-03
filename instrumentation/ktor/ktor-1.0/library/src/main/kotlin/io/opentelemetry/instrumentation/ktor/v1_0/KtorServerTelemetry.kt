/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource
import kotlinx.coroutines.withContext
import java.util.function.UnaryOperator

class KtorServerTelemetry private constructor(
  private val instrumenter: Instrumenter<ApplicationRequest, ApplicationResponse>,
) {

  class Configuration {
    internal lateinit var builder: DefaultHttpServerInstrumenterBuilder<ApplicationRequest, ApplicationResponse>

    internal var spanKindExtractor:
      (SpanKindExtractor<ApplicationRequest>) -> SpanKindExtractor<ApplicationRequest> = { a -> a }

    fun openTelemetry(openTelemetry: OpenTelemetry) {
      this.builder =
        DefaultHttpServerInstrumenterBuilder.create(
          INSTRUMENTATION_NAME,
          openTelemetry,
          KtorHttpServerAttributesGetter
        )
    }

    fun spanStatusExtractor(
      extractor: (SpanStatusExtractor<ApplicationRequest, ApplicationResponse>) -> SpanStatusExtractor<ApplicationRequest, ApplicationResponse>
    ) {
      builder.setSpanStatusExtractorCustomizer { prevExtractor ->
        SpanStatusExtractor {
            spanStatusBuilder: SpanStatusBuilder,
            request: ApplicationRequest,
            response: ApplicationResponse?,
            throwable: Throwable?
          ->
          extractor(prevExtractor).extract(spanStatusBuilder, request, response, throwable)
        }
      }
    }

    fun spanKindExtractor(extractor: (SpanKindExtractor<ApplicationRequest>) -> SpanKindExtractor<ApplicationRequest>) {
      this.spanKindExtractor = extractor
    }

    fun spanNameExtractor(extractor: UnaryOperator<SpanNameExtractor<ApplicationRequest>>) {
      builder.setSpanNameExtractorCustomizer(extractor)
    }

    fun attributesExtractor(extractor: AttributesExtractor<ApplicationRequest, ApplicationResponse>) {
      builder.addAttributesExtractor(extractor)
    }

    /**
     * Configures which HTTP request headers are captured as span attributes.
     *
     * Header values are captured under the `http.request.header.<key>` attribute key. The `<key>`
     * part in the attribute key is the lowercase header name.
     *
     * Selector patterns are matched case-insensitively, since HTTP header names are case-
     * insensitive. `?` matches one character and `*` matches any number of characters, including
     * none. Excluded patterns take precedence over included patterns. A selector with no included
     * patterns captures every header that is not excluded, and an [empty][IncludeExclude.isEmpty]
     * selector captures no headers.
     */
    fun requestHeaders(requestHeaders: IncludeExclude) {
      builder.setRequestHeaders(requestHeaders)
    }

    /**
     * Configures which HTTP response headers are captured as span attributes.
     *
     * Header values are captured under the `http.response.header.<key>` attribute key. The `<key>`
     * part in the attribute key is the lowercase header name.
     *
     * Selector patterns are matched case-insensitively, since HTTP header names are case-
     * insensitive. `?` matches one character and `*` matches any number of characters, including
     * none. Excluded patterns take precedence over included patterns. A selector with no included
     * patterns captures every header that is not excluded, and an [empty][IncludeExclude.isEmpty]
     * selector captures no headers.
     */
    fun responseHeaders(responseHeaders: IncludeExclude) {
      builder.setResponseHeaders(responseHeaders)
    }

    fun knownMethods(knownMethods: Collection<String>) {
      builder.setKnownMethods(knownMethods)
    }

    internal fun isOpenTelemetryInitialized(): Boolean = this::builder.isInitialized
  }

  private fun start(call: ApplicationCall): Context? {
    val parentContext = Context.current()
    if (!instrumenter.shouldStart(parentContext, call.request)) {
      return null
    }

    return instrumenter.start(parentContext, call.request)
  }

  private fun end(context: Context, call: ApplicationCall, error: Throwable?) {
    instrumenter.end(context, call.request, call.response, error)
  }

  companion object Feature : ApplicationFeature<Application, Configuration, KtorServerTelemetry> {
    private const val INSTRUMENTATION_NAME = "io.opentelemetry.ktor-1.0"

    private val CONTEXT_KEY = AttributeKey<Context>("OpenTelemetry")
    private val ERROR_KEY = AttributeKey<Throwable>("OpenTelemetryException")

    override val key: AttributeKey<KtorServerTelemetry> = AttributeKey("OpenTelemetry")

    override fun install(pipeline: Application, configure: Configuration.() -> Unit): KtorServerTelemetry {
      val configuration = Configuration().apply(configure)

      if (!configuration.isOpenTelemetryInitialized()) {
        throw IllegalArgumentException("OpenTelemetry must be set")
      }

      val instrumenter = InstrumenterUtil.buildUpstreamInstrumenter(
        configuration.builder.instrumenterBuilder(),
        ApplicationRequestGetter,
        configuration.spanKindExtractor(SpanKindExtractor.alwaysServer())
      )

      val feature = KtorServerTelemetry(instrumenter)

      val startPhase = PipelinePhase("OpenTelemetry")
      pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, startPhase)
      pipeline.intercept(startPhase) {
        val context = feature.start(call)

        if (context != null) {
          call.attributes.put(CONTEXT_KEY, context)
          withContext(context.asContextElement()) {
            try {
              proceed()
            } catch (t: Throwable) {
              // Stash error for reporting later since need ktor to finish setting up the response
              call.attributes.put(ERROR_KEY, t)
              throw t
            }
          }
        } else {
          proceed()
        }
      }

      val postSendPhase = PipelinePhase("OpenTelemetryPostSend")
      pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.After, postSendPhase)
      pipeline.sendPipeline.intercept(postSendPhase) {
        val context = call.attributes.getOrNull(CONTEXT_KEY)
        if (context != null) {
          var error: Throwable? = call.attributes.getOrNull(ERROR_KEY)
          try {
            proceed()
          } catch (t: Throwable) {
            error = t
            throw t
          } finally {
            feature.end(context, call, error)
          }
        } else {
          proceed()
        }
      }

      pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
        val context = call.attributes.getOrNull(CONTEXT_KEY)
        if (context != null) {
          HttpServerRoute.update(context, HttpServerRouteSource.SERVER, { _, arg -> arg!!.route.parent.toString() }, call)
        }
      }

      return feature
    }
  }
}
