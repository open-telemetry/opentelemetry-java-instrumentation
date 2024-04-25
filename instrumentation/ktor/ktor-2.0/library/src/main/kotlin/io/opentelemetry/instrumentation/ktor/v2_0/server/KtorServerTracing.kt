/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor
import io.opentelemetry.instrumentation.ktor.v2_0.InstrumentationProperties.INSTRUMENTATION_NAME
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

    @Deprecated("Please use method `spanStatusExtractor`")
    fun setStatusExtractor(
      extractor: (SpanStatusExtractor<ApplicationRequest, ApplicationResponse>) -> SpanStatusExtractor<in ApplicationRequest, in ApplicationResponse>
    ) {
      spanStatusExtractor { prevStatusExtractor ->
        extractor(prevStatusExtractor).extract(spanStatusBuilder, request, response, error)
      }
    }

    fun spanStatusExtractor(extract: SpanStatusData.(SpanStatusExtractor<ApplicationRequest, ApplicationResponse>) -> Unit) {
      statusExtractor = { prevExtractor ->
        SpanStatusExtractor<ApplicationRequest, ApplicationResponse> { spanStatusBuilder: SpanStatusBuilder,
                                                                       request: ApplicationRequest,
                                                                       response: ApplicationResponse?,
                                                                       throwable: Throwable? ->
          extract(SpanStatusData(spanStatusBuilder, request, response, throwable), prevExtractor)
        }
      }
    }

    data class SpanStatusData(
      val spanStatusBuilder: SpanStatusBuilder,
      val request: ApplicationRequest,
      val response: ApplicationResponse?,
      val error: Throwable?
    )

    @Deprecated("Please use method `spanKindExtractor`")
    fun setSpanKindExtractor(extractor: (SpanKindExtractor<ApplicationRequest>) -> SpanKindExtractor<ApplicationRequest>) {
      spanKindExtractor { prevSpanKindExtractor ->
        extractor(prevSpanKindExtractor).extract(this)
      }
    }

    fun spanKindExtractor(extract: ApplicationRequest.(SpanKindExtractor<ApplicationRequest>) -> SpanKind) {
      spanKindExtractor = { prevExtractor ->
        SpanKindExtractor<ApplicationRequest> { request: ApplicationRequest ->
          extract(request, prevExtractor)
        }
      }
    }

    @Deprecated("Please use method `attributeExtractor`")
    fun addAttributeExtractor(extractor: AttributesExtractor<in ApplicationRequest, in ApplicationResponse>) {
      attributeExtractor {
        onStart {
          extractor.onStart(attributes, parentContext, request)
        }
        onEnd {
          extractor.onEnd(attributes, parentContext, request, response, error)
        }
      }
    }

    fun attributeExtractor(extractorBuilder: ExtractorBuilder.() -> Unit = {}) {
      val builder = ExtractorBuilder().apply(extractorBuilder).build()
      additionalExtractors.add(
        object : AttributesExtractor<ApplicationRequest, ApplicationResponse> {
          override fun onStart(attributes: AttributesBuilder, parentContext: Context, request: ApplicationRequest) {
            builder.onStart(OnStartData(attributes, parentContext, request))
          }

          override fun onEnd(attributes: AttributesBuilder, context: Context, request: ApplicationRequest, response: ApplicationResponse?, error: Throwable?) {
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
      val request: ApplicationRequest
    )

    data class OnEndData(
      val attributes: AttributesBuilder,
      val parentContext: Context,
      val request: ApplicationRequest,
      val response: ApplicationResponse?,
      val error: Throwable?
    )

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
        httpServerRouteBuilder.setKnownMethods(this)
      }
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

  companion object Feature : BaseApplicationPlugin<Application, Configuration, KtorServerTracing> {

    private val contextKey = AttributeKey<Context>("OpenTelemetry")
    private val errorKey = AttributeKey<Throwable>("OpenTelemetryException")

    override val key: AttributeKey<KtorServerTracing> = AttributeKey("OpenTelemetry")

    override fun install(pipeline: Application, configure: Configuration.() -> Unit): KtorServerTracing {
      val configuration = Configuration().apply(configure)

      require(configuration.isOpenTelemetryInitialized()) { "OpenTelemetry must be set" }

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
        HttpServerRoute.update(Context.current(), HttpServerRouteSource.SERVER, { _, arg -> arg.route.parent.toString() }, call)
      }

      return feature
    }
  }
}
