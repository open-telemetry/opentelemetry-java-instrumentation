/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KtorClientTracing internal constructor(
  private val instrumenter: Instrumenter<HttpRequestData, HttpResponse>,
  private val propagators: ContextPropagators,
) {

  private fun createSpan(requestBuilder: HttpRequestBuilder): Context? {
    val parentContext = Context.current()
    val requestData = requestBuilder.build()

    return if (instrumenter.shouldStart(parentContext, requestData)) {
      instrumenter.start(parentContext, requestData)
    } else {
      null
    }
  }

  private fun populateRequestHeaders(requestBuilder: HttpRequestBuilder, context: Context) {
    propagators.textMapPropagator.inject(context, requestBuilder, KtorHttpHeadersSetter)
  }

  private fun endSpan(context: Context, call: HttpClientCall, error: Throwable?) {
    endSpan(context, HttpRequestBuilder().takeFrom(call.request), call.response, error)
  }

  private fun endSpan(context: Context, requestBuilder: HttpRequestBuilder, response: HttpResponse?, error: Throwable?) {
    instrumenter.end(context, requestBuilder.build(), response, error)
  }

  companion object : HttpClientPlugin<KtorClientTracingBuilder, KtorClientTracing> {

    private val openTelemetryContextKey = AttributeKey<Context>("OpenTelemetry")

    override val key = AttributeKey<KtorClientTracing>("OpenTelemetry")

    override fun prepare(block: KtorClientTracingBuilder.() -> Unit) = KtorClientTracingBuilder().apply(block).build()

    override fun install(plugin: KtorClientTracing, scope: HttpClient) {
      installSpanCreation(plugin, scope)
      installSpanEnd(plugin, scope)
    }

    private fun installSpanCreation(plugin: KtorClientTracing, scope: HttpClient) {
      val createSpanPhase = PipelinePhase("OpenTelemetryCreateSpan")
      scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, createSpanPhase)

      scope.sendPipeline.intercept(createSpanPhase) {
        val requestBuilder = context
        val openTelemetryContext = plugin.createSpan(requestBuilder)

        if (openTelemetryContext != null) {
          try {
            requestBuilder.attributes.put(openTelemetryContextKey, openTelemetryContext)
            plugin.populateRequestHeaders(requestBuilder, openTelemetryContext)

            withContext(openTelemetryContext.asContextElement()) { proceed() }
          } catch (e: Throwable) {
            plugin.endSpan(openTelemetryContext, requestBuilder, null, e)
            throw e
          }
        } else {
          proceed()
        }
      }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun installSpanEnd(plugin: KtorClientTracing, scope: HttpClient) {
      val endSpanPhase = PipelinePhase("OpenTelemetryEndSpan")
      scope.receivePipeline.insertPhaseBefore(HttpReceivePipeline.State, endSpanPhase)

      scope.receivePipeline.intercept(endSpanPhase) {
        val openTelemetryContext = it.call.attributes.getOrNull(openTelemetryContextKey)
        openTelemetryContext ?: return@intercept

        scope.launch {
          val job = it.call.coroutineContext.job
          job.join()
          val cause = if (!job.isCancelled) {
            null
          } else {
            kotlin.runCatching { job.getCancellationException() }.getOrNull()
          }

          plugin.endSpan(openTelemetryContext, it.call, cause)
        }
      }
    }
  }
}
