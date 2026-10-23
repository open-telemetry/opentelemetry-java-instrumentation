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
 * Returns whether this is a known Ktor version that needs the legacy interceptor workaround.
 * Unknown versions deliberately use the dispatcher wrapper, which is compatible with Ktor 3.4.3+
 * and avoids relying on package implementation metadata that may be missing from fat JARs.
 */
internal fun resolveKtorVersion(implementationVersion: String?, codeSourceFileName: String?): String? {
  if (implementationVersion != null) return implementationVersion
  val match = Regex("^ktor-server-core(?:-jvm)?-(\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?)\\.jar$")
    .matchEntire(codeSourceFileName ?: return null) ?: return null
  return match.groupValues[1]
}

internal fun requiresLegacyKtorInterceptor(version: String?): Boolean {
  val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:([-+].*))?$").matchEntire(version ?: return false) ?: return false
  val (major, minor, patch, suffix) = match.destructured
  // Prereleases are not assumed to include the Ktor fix, even when their numeric version is high enough.
  if (suffix.startsWith("-")) return true
  val majorVersion = major.toIntOrNull() ?: return false
  val minorVersion = minor.toIntOrNull() ?: return false
  val patchVersion = patch.toIntOrNull() ?: return false
  return majorVersion < 3 || (majorVersion == 3 && (minorVersion < 4 || (minorVersion == 4 && patchVersion < 3)))
}

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
object KtorServerTelemetryUtil {
  // Read once because this decision is on the per-request instrumentation path.
  private val ktorVersion = resolveKtorVersion(
    Application::class.java.`package`.implementationVersion,
    Application::class.java.protectionDomain?.codeSource?.location?.path?.substringAfterLast('/'),
  )
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
        val extra = when {
          dispatcher == null -> EmptyCoroutineContext
          requiresLegacyKtorInterceptor(ktorVersion) -> EmptyInterceptor(dispatcher)
          else -> ContextLeakWorkaroundDispatcher(dispatcher)
        }
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

  // Ktor versions before 3.4.3 can leak coroutine thread context when propagating OTel context
  // with withContext(context.asContextElement()). For known affected versions, a plain interceptor
  // works around the leak. Ktor 3.4.3 fixes the leak when the interceptor remains a dispatcher. Use
  // a dispatcher wrapper for fixed or unknown versions so fat JARs without implementation metadata
  // do not accidentally select the legacy workaround.
  private class EmptyInterceptor(val dispatcher: CoroutineDispatcher) : ContinuationInterceptor {
    override val key: CoroutineContext.Key<*> = ContinuationInterceptor

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = dispatcher.interceptContinuation(continuation)

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
      dispatcher.releaseInterceptedContinuation(continuation)
    }
  }

  private class ContextLeakWorkaroundDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = delegate.dispatch(context, block)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)
  }
}
