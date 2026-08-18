/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.*
import io.opentelemetry.instrumentation.api.config.IncludeExclude
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testingExtension = HttpClientInstrumentationExtension.forLibrary()
  }

  override fun HttpClientConfig<*>.installTracing() {
    install(KtorClientTelemetry) {
      setOpenTelemetry(testingExtension.openTelemetry)
      // the wildcard patterns exercise capturing headers by name enumeration
      requestHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
      responseHeaders(IncludeExclude.builder().setIncluded("X-Test-*").build())
    }
  }
}
