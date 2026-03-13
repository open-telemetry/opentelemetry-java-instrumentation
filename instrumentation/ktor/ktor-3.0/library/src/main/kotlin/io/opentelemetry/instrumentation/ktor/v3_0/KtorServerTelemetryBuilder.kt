/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource
import io.opentelemetry.instrumentation.ktor.common.v2_0.AbstractKtorServerTelemetryBuilder
import io.opentelemetry.instrumentation.ktor.common.v2_0.internal.KtorServerTelemetryUtil.configureTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.InstrumentationProperties.INSTRUMENTATION_NAME
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor

class KtorServerTelemetryBuilder internal constructor(
  instrumentationName: String
) : AbstractKtorServerTelemetryBuilder(instrumentationName)

val emptyInterceptor = object : ContinuationInterceptor {
  override val key = ContinuationInterceptor

  override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = object : Continuation<T> {
    override val context = continuation.context

    override fun resumeWith(result: Result<T>) {
      continuation.resumeWith(result)
    }
  }
}

val KtorServerTelemetry = createRouteScopedPlugin("OpenTelemetry", { KtorServerTelemetryBuilder(INSTRUMENTATION_NAME) }) {
  require(pluginConfig.isOpenTelemetryInitialized()) { "OpenTelemetry must be set" }

  configureTelemetry(pluginConfig, application) { action ->
    // work around issue described in https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16430
    withContext(emptyInterceptor) {
      action()
    }
  }

  application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
    HttpServerRoute.update(Context.current(), HttpServerRouteSource.SERVER, { _, arg -> arg!!.route.parent.toString() }, call)
  }
}
