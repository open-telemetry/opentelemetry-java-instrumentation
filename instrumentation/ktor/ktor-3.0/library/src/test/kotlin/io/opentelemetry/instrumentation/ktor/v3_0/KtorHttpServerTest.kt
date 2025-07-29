/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.response.respondText
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpServerTest : AbstractKtorHttpServerTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    val TESTING: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()
  }

  override fun getTesting(): InstrumentationExtension = TESTING

  override fun installOpenTelemetry(application: Application) {
    application.apply {
      install(KtorServerTelemetry) {
        setOpenTelemetry(TESTING.openTelemetry)
        capturedRequestHeaders(TEST_REQUEST_HEADER)
        capturedResponseHeaders(TEST_RESPONSE_HEADER)
      }

      install(createRouteScopedPlugin("Failure handler, that can mask exceptions if exception handling is in the wrong phase", ServerEndpoint.EXCEPTION.path, {}) {
        on(CallFailed) { call, cause ->
          call.respondText("failure: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
      })
    }
  }
}
