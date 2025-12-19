/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.server.application.install
import io.opentelemetry.instrumentation.ktor.v2_0.common.internal.Experimental
import io.opentelemetry.instrumentation.ktor.v3_0.InstrumentationProperties.INSTRUMENTATION_NAME
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
    val testing: InstrumentationExtension = HttpServerInstrumentationExtension.forLibrary()
  }

  override fun serverInstall(application: io.ktor.server.application.Application) {
    application.install(KtorServerTelemetry) {
      setOpenTelemetry(testing.openTelemetry)
      Experimental.emitExperimentalTelemetry(this)
    }
  }

  override fun instrumentationName(): String = INSTRUMENTATION_NAME
}
