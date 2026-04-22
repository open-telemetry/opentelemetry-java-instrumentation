/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0

import io.ktor.server.application.*
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpServerTest : AbstractKtorHttpServerTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forAgent()
  }

  override fun getTesting(): InstrumentationExtension = testing

  override fun installOpenTelemetry(application: Application) {
  }

  override fun configure(options: HttpServerTestOptions) {
    super.configure(options)
    options.setTestException(false)
  }
}
