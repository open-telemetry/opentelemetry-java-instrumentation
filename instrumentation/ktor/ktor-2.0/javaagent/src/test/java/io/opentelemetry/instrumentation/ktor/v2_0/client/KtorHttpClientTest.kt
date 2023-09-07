/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension
import kotlinx.coroutines.*
import org.junit.jupiter.api.extension.RegisterExtension

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  companion object {
    @JvmStatic
    @RegisterExtension
    private val TESTING = HttpClientInstrumentationExtension.forAgent()
  }

  override fun HttpClientConfig<*>.installTracing() {
  }
}
