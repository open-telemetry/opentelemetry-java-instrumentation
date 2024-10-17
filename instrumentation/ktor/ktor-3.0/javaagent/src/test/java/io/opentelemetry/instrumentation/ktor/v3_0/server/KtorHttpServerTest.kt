/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0.server

import io.ktor.server.application.*
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpServerTest : AbstractKtorHttpServerTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    val TESTING: InstrumentationExtension = HttpServerInstrumentationExtension.forAgent()
  }

  override fun getTesting(): InstrumentationExtension {
    return TESTING
  }

  override fun installOpenTelemetry(application: Application) {
  }

  override fun configure(options: HttpServerTestOptions) {
    super.configure(options)
    options.setTestException(false)
  }
}
