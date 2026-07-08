/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0.internal

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount
import io.opentelemetry.instrumentation.ktor.common.v2_0.AbstractKtorClientTelemetry
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
object KtorClientTelemetryUtil {
  private val OPEN_TELEMETRY_CONTEXT_KEY = AttributeKey<Context>("OpenTelemetry")
  private val OPEN_TELEMETRY_PARENT_CONTEXT_KEY = AttributeKey<Context>("OpenTelemetryParent")

  /** Returns the OpenTelemetry [Context] holding the client span for [call], or null if absent. */
  @JvmStatic
  fun getOpenTelemetryContext(call: HttpClientCall): Context? = call.attributes.getOrNull(OPEN_TELEMETRY_CONTEXT_KEY)

  @JvmStatic
  @Suppress("UNCHECKED_CAST")
  fun wrapWithClientSpanContext(block: Any?): Any? {
    block ?: return null
    val original = block as suspend (HttpResponse) -> Any?
    val wrapped: suspend (HttpResponse) -> Any? = { response ->
      val otelContext = response.call.attributes.getOrNull(OPEN_TELEMETRY_CONTEXT_KEY)
      if (otelContext != null) {
        withContext(otelContext.asContextElement()) { original(response) }
      } else {
        original(response)
      }
    }
    return wrapped
  }

  fun install(plugin: AbstractKtorClientTelemetry, scope: HttpClient) {
    installSpanCreation(plugin, scope)
    installSpanEnd(plugin, scope)
  }

  private fun installSpanCreation(plugin: AbstractKtorClientTelemetry, scope: HttpClient) {
    val initializeRequestPhase = PipelinePhase("OpenTelemetryInitializeRequest")
    scope.requestPipeline.insertPhaseAfter(HttpRequestPipeline.State, initializeRequestPhase)

    scope.requestPipeline.intercept(initializeRequestPhase) {
      val openTelemetryContext = HttpClientRequestResendCount.initialize(Context.current())
      context.attributes.put(OPEN_TELEMETRY_PARENT_CONTEXT_KEY, openTelemetryContext)
      proceed()
    }

    val createSpanPhase = PipelinePhase("OpenTelemetryCreateSpan")
    scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, createSpanPhase)

    scope.sendPipeline.intercept(createSpanPhase) {
      val requestBuilder = context
      val parentContext = requestBuilder.attributes[OPEN_TELEMETRY_PARENT_CONTEXT_KEY]
      val openTelemetryContext = plugin.createSpan(requestBuilder, parentContext)

      if (openTelemetryContext != null) {
        try {
          requestBuilder.attributes.put(OPEN_TELEMETRY_CONTEXT_KEY, openTelemetryContext)
          plugin.populateRequestHeaders(requestBuilder, openTelemetryContext)

          withContext(openTelemetryContext.asContextElement()) { proceed() }
        } catch (t: Throwable) {
          plugin.endSpan(openTelemetryContext, requestBuilder, null, t)
          throw t
        }
      } else {
        proceed()
      }
    }
  }

  private fun installSpanEnd(plugin: AbstractKtorClientTelemetry, scope: HttpClient) {
    val endSpanPhase = PipelinePhase("OpenTelemetryEndSpan")
    scope.receivePipeline.insertPhaseBefore(HttpReceivePipeline.State, endSpanPhase)

    scope.receivePipeline.intercept(endSpanPhase) {
      val openTelemetryContext = it.call.attributes.getOrNull(OPEN_TELEMETRY_CONTEXT_KEY)
      openTelemetryContext ?: return@intercept

      val job = it.call.coroutineContext.job
      val call = it.call

      job.invokeOnCompletion { cause ->
        plugin.endSpan(openTelemetryContext, call, cause)
      }
    }
  }
}
