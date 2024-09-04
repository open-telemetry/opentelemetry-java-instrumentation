/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.*

class KtorHttpClientTest : AbstractKtorHttpClientTest() {

  override fun HttpClientConfig<*>.installTracing() {
  }
}
