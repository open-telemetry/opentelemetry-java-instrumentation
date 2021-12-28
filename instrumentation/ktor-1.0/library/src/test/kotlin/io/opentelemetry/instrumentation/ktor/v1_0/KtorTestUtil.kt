/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.test.base.HttpServerTest

class KtorTestUtil {
  companion object {
    fun installOpenTelemetry(application: Application, openTelemetry: OpenTelemetry) {
      application.install(KtorServerTracing) {
        setOpenTelemetry(openTelemetry)
        captureHttpHeaders(HttpServerTest.capturedHttpHeadersForTesting())
      }
    }
  }
}
