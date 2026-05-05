/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0.internal

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil
import io.opentelemetry.instrumentation.ktor.common.v2_0.AbstractKtorServerTelemetryBuilder
import io.opentelemetry.instrumentation.ktor.common.v2_0.ApplicationRequestGetter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
object KtorServerTelemetryUtil {

  fun configureTelemetry(builder: AbstractKtorServerTelemetryBuilder, application: Application) {
    val contextKey = AttributeKey<Context>("OpenTelemetry")
    val errorKey = AttributeKey<Throwable>("OpenTelemetryException")
    val processedKey = AttributeKey<Unit>("OpenTelemetryProcessed")

    val instrumenter = instrumenter(builder)
    val tracer = KtorServerTracer(instrumenter)

    val startPhase = PipelinePhase("OpenTelemetry")
    application.insertPhaseBefore(ApplicationCallPipeline.Setup, startPhase)
    application.intercept(startPhase) {
      val context = tracer.start(call)

      if (context != null) {
        call.attributes.put(contextKey, context)
        val dispatcher = coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
        val extra = if (dispatcher != null) EmptyInterceptor(dispatcher) else EmptyCoroutineContext
        withContext(context.asContextElement() + extra) {
          proceed()
        }
      } else {
        proceed()
      }
    }

    val errorPhase = PipelinePhase("OpenTelemetryError")
    application.insertPhaseBefore(ApplicationCallPipeline.Monitoring, errorPhase)
    application.intercept(errorPhase) {
      try {
        proceed()
      } catch (t: Throwable) {
        // Stash error for reporting later since need ktor to finish setting up the response
        call.attributes.put(errorKey, t)
        throw t
      }
    }

    val postSendPhase = PipelinePhase("OpenTelemetryPostSend")
    application.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.After, postSendPhase)
    application.sendPipeline.intercept(postSendPhase) {
      val context = call.attributes.getOrNull(contextKey)
      if (context != null && !call.attributes.contains(processedKey)) {
        var error: Throwable? = call.attributes.getOrNull(errorKey)
        try {
          proceed()
        } catch (t: Throwable) {
          error = t
          throw t
        } finally {
          tracer.end(context, call, error)
          call.attributes.put(processedKey, Unit)
        }
      } else {
        proceed()
      }
    }
  }

  private fun instrumenter(builder: AbstractKtorServerTelemetryBuilder): Instrumenter<ApplicationRequest, ApplicationResponse> = InstrumenterUtil.buildUpstreamInstrumenter(
    builder.builder.instrumenterBuilder(),
    ApplicationRequestGetter,
    builder.spanKindExtractor(SpanKindExtractor.alwaysServer())
  )

  // A no-op ContinuationInterceptor. There seems to be a bug in Ktor where when propagate otel
  // context by using withContext(context.asContextElement()) updateThreadContext, that activates
  // the otel scope, gets called in
  // https://github.com/open-telemetry/opentelemetry-java/blob/main/extensions/kotlin/src/main/java/io/opentelemetry/extension/kotlin/KotlinContextElement.java
  // but the restoreThreadContext is not always called, which causes the otel context to leak across
  // requests. This issue can be worked around by adding -Dio.ktor.internal.disable.sfg=true to jvm
  // arguments. Adding this no-op interceptor seems to also work around the issue.
  // See https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16430
  private class EmptyInterceptor(val dispatcher: CoroutineDispatcher) : ContinuationInterceptor {
    override val key: CoroutineContext.Key<*> = ContinuationInterceptor

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = dispatcher.interceptContinuation(continuation)

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
      dispatcher.releaseInterceptedContinuation(continuation)
    }
  }
}
