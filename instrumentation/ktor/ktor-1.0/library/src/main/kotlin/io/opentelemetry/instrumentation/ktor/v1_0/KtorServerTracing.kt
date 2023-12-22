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
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor
import kotlinx.coroutines.withContext

class KtorServerTracing private constructor(
  private val instrumenter: Instrumenter<ApplicationRequest, ApplicationResponse>,
) {

  class Configuration {
    internal lateinit var openTelemetry: OpenTelemetry

    internal val additionalExtractors = mutableListOf<AttributesExtractor<in ApplicationRequest, in ApplicationResponse>>()

    internal val httpAttributesExtractorBuilder = HttpServerAttributesExtractor.builder(KtorHttpServerAttributesGetter.INSTANCE)

    internal val httpSpanNameExtractorBuilder = HttpSpanNameExtractor.builder(KtorHttpServerAttributesGetter.INSTANCE)

    internal val httpServerRouteBuilder = HttpServerRoute.builder(KtorHttpServerAttributesGetter.INSTANCE)

    internal var statusExtractor:
      (SpanStatusExtractor<ApplicationRequest, ApplicationResponse>) -> SpanStatusExtractor<in ApplicationRequest, in ApplicationResponse> = { a -> a }

    internal var spanKindExtractor:
      (SpanKindExtractor<ApplicationRequest>) -> SpanKindExtractor<ApplicationRequest> = { a -> a }

    fun setOpenTelemetry(openTelemetry: OpenTelemetry) {
      this.openTelemetry = openTelemetry
    }

    fun setStatusExtractor(
      extractor: (SpanStatusExtractor<ApplicationRequest, ApplicationResponse>) -> SpanStatusExtractor<in ApplicationRequest, in ApplicationResponse>
    ) {
      this.statusExtractor = extractor
    }

    fun setSpanKindExtractor(extractor: (SpanKindExtractor<ApplicationRequest>) -> SpanKindExtractor<ApplicationRequest>) {
      this.spanKindExtractor = extractor
    }

    fun addAttributeExtractor(extractor: AttributesExtractor<in ApplicationRequest, in ApplicationResponse>) {
      additionalExtractors.add(extractor)
    }

    fun setCapturedRequestHeaders(requestHeaders: List<String>) {
      httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders)
    }

    fun setCapturedResponseHeaders(responseHeaders: List<String>) {
      httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders)
    }

    fun setKnownMethods(knownMethods: Set<String>) {
      httpAttributesExtractorBuilder.setKnownMethods(knownMethods)
      httpSpanNameExtractorBuilder.setKnownMethods(knownMethods)
      httpServerRouteBuilder.setKnownMethods(knownMethods)
    }

    internal fun isOpenTelemetryInitialized(): Boolean = this::openTelemetry.isInitialized
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

  companion object Feature : ApplicationFeature<Application, Configuration, KtorServerTracing> {
    private val INSTRUMENTATION_NAME = "io.opentelemetry.ktor-1.0"

    private val contextKey = AttributeKey<Context>("OpenTelemetry")
    private val errorKey = AttributeKey<Throwable>("OpenTelemetryException")

    override val key: AttributeKey<KtorServerTracing> = AttributeKey("OpenTelemetry")

    override fun install(pipeline: Application, configure: Configuration.() -> Unit): KtorServerTracing {
      val configuration = Configuration().apply(configure)

      if (!configuration.isOpenTelemetryInitialized()) {
        throw IllegalArgumentException("OpenTelemetry must be set")
      }

      val httpAttributesGetter = KtorHttpServerAttributesGetter.INSTANCE

      val instrumenterBuilder = Instrumenter.builder<ApplicationRequest, ApplicationResponse>(
        configuration.openTelemetry,
        INSTRUMENTATION_NAME,
        configuration.httpSpanNameExtractorBuilder.build()
      )

      configuration.additionalExtractors.forEach { instrumenterBuilder.addAttributesExtractor(it) }

      with(instrumenterBuilder) {
        setSpanStatusExtractor(configuration.statusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter)))
        addAttributesExtractor(configuration.httpAttributesExtractorBuilder.build())
        addOperationMetrics(HttpServerMetrics.get())
        addContextCustomizer(configuration.httpServerRouteBuilder.build())
      }

      val instrumenter = InstrumenterUtil.buildUpstreamInstrumenter(
        instrumenterBuilder,
        ApplicationRequestGetter,
        configuration.spanKindExtractor(SpanKindExtractor.alwaysServer())
      )

      val feature = KtorServerTracing(instrumenter)

      val startPhase = PipelinePhase("OpenTelemetry")
      pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, startPhase)
      pipeline.intercept(startPhase) {
        val context = feature.start(call)

        if (context != null) {
          call.attributes.put(contextKey, context)
          withContext(context.asContextElement()) {
            try {
              proceed()
            } catch (err: Throwable) {
              // Stash error for reporting later since need ktor to finish setting up the response
              call.attributes.put(errorKey, err)
              throw err
            }
          }
        } else {
          proceed()
        }
      }

      val postSendPhase = PipelinePhase("OpenTelemetryPostSend")
      pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.After, postSendPhase)
      pipeline.sendPipeline.intercept(postSendPhase) {
        val context = call.attributes.getOrNull(contextKey)
        if (context != null) {
          var error: Throwable? = call.attributes.getOrNull(errorKey)
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
        val context = call.attributes.getOrNull(contextKey)
        if (context != null) {
          HttpServerRoute.update(context, HttpServerRouteSource.SERVER, { _, arg -> arg.route.parent.toString() }, call)
        }
      }

      return feature
    }
  }
}
