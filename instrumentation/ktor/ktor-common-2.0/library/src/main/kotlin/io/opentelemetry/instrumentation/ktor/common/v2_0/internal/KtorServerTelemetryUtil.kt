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
internal fun isKtorContextLeakFixAvailable(version: String?): Boolean {
  // Only treat final releases as fixed. Prerelease versions may not contain the fix yet.
  val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+.*)?$").matchEntire(version ?: return false) ?: return false
  val (major, minor, patch) = match.destructured
  val majorVersion = major.toIntOrNull() ?: return false
  val minorVersion = minor.toIntOrNull() ?: return false
  val patchVersion = patch.toIntOrNull() ?: return false
  return majorVersion > 3 || (majorVersion == 3 && (minorVersion > 4 || (minorVersion == 4 && patchVersion >= 3)))
}

object KtorServerTelemetryUtil {
  // Read once because this decision is on the per-request instrumentation path.
  private val ktorHasContextLeakFix = isKtorContextLeakFixAvailable(Application::class.java.`package`.implementationVersion)

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
        val extra = if (dispatcher != null && !ktorHasContextLeakFix) EmptyInterceptor(dispatcher) else EmptyCoroutineContext
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

  // Ktor has a coroutine context leak where propagating OTel context with
  // withContext(context.asContextElement()) can activate an OTel scope without reliably restoring
  // it. Adding this interceptor works around the issue on Ktor versions before 3.4.3 (see
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16430). Ktor 3.4.3+
  // fixes the leak itself, but only when its original ContinuationInterceptor remains a dispatcher.
  private class EmptyInterceptor(val dispatcher: CoroutineDispatcher) : ContinuationInterceptor {
    override val key: CoroutineContext.Key<*> = ContinuationInterceptor

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = dispatcher.interceptContinuation(continuation)

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
      dispatcher.releaseInterceptedContinuation(continuation)
    }
  }
}
