/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.api.config.IncludeExclude

internal object KtorTestUtil {
  fun installOpenTelemetry(application: Application, openTelemetry: OpenTelemetry) {
    application.install(KtorServerTelemetry) {
      setOpenTelemetry(openTelemetry)
      // the wildcard patterns exercise capturing headers by name enumeration
      setRequestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
      setResponseHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
    }
  }
}
