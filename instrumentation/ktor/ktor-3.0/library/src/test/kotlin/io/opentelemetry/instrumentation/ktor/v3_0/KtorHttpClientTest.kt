/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.*
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val TESTING = HttpClientInstrumentationExtension.forLibrary()
  }

  override fun HttpClientConfig<*>.installTracing() {
    install(KtorClientTelemetry) {
      setOpenTelemetry(TESTING.openTelemetry)
      capturedRequestHeaders(TEST_REQUEST_HEADER)
      capturedResponseHeaders(TEST_RESPONSE_HEADER)
    }
  }
}
