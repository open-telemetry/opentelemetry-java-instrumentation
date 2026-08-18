/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0

import io.ktor.server.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.api.config.IncludeExclude

internal object KtorTestUtil {
  fun installOpenTelemetry(application: Application, openTelemetry: OpenTelemetry) {
    application.install(KtorServerTelemetry) {
      setOpenTelemetry(openTelemetry)
      // the wildcard patterns exercise capturing headers by name enumeration
      requestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
      responseHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
    }
  }
}
