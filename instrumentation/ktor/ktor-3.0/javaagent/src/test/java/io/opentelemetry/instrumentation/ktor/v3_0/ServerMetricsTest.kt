/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.server.application.Application
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.RegisterExtension

@EnabledIfSystemProperty(
  named = "otel.instrumentation.http.server.emit-experimental-telemetry",
  matches = "true"
)
class ServerMetricsTest : AbstractKtorServerMetricsTest() {
  companion object {
    @JvmStatic
    @RegisterExtension
    val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forAgent()
  }

  override fun serverInstall(application: Application) {
    // javaagent automatically instruments the application
  }

  // For javaagent, HTTP server metrics are emitted by the Netty instrumentation
  // since Ktor runs on top of Netty as its HTTP engine
  override fun instrumentationName(): String = "io.opentelemetry.netty-4.1"
}
