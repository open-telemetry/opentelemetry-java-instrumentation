/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0

import io.ktor.client.HttpClientConfig
import io.opentelemetry.instrumentation.testing.junit.http.AgentHttpClientInstrumentationExtension
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val testing = AgentHttpClientInstrumentationExtension.create()
  }

  override fun HttpClientConfig<*>.installTracing() {
  }
}
