/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0.server

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.routing.RoutingRoot.Plugin.RoutingCallStarted
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource
import io.opentelemetry.instrumentation.ktor.v2_0.server.KtorServerTracing
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpServerInstrumentationTest : AbstractKtorHttpServerTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    val TESTING: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()
  }

  override fun getTesting(): InstrumentationExtension {
    return TESTING
  }

  override fun installOpenTelemetry(application: Application) {
    application.apply {
      install(KtorServerTracing) {
        setOpenTelemetry(TESTING.openTelemetry)
        capturedRequestHeaders(TEST_REQUEST_HEADER)
        capturedResponseHeaders(TEST_RESPONSE_HEADER)
      }
    }
  }

  override fun configure(options: HttpServerTestOptions) {
    super.configure(options)
    options.setTestException(false)
  }
}
