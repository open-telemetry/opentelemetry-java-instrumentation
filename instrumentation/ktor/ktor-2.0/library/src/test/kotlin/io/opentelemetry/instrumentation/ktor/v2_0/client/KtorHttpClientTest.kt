/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

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
    install(KtorClientTracing) {
      setOpenTelemetry(TESTING.openTelemetry)
      setCapturedRequestHeaders(listOf(TEST_REQUEST_HEADER))
      setCapturedResponseHeaders(listOf(TEST_RESPONSE_HEADER))
    }
  }
}
