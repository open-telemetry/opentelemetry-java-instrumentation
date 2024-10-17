/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0.server

import io.ktor.server.application.*
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpServerTest : AbstractKtorHttpServerTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    val TESTING: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()
  }

  override fun getTesting(): InstrumentationExtension {
    return TESTING
  }

  override fun installOpenTelemetry(application: Application) {
    KtorTestUtil.installOpenTelemetry(application, TESTING.openTelemetry)
  }
}
