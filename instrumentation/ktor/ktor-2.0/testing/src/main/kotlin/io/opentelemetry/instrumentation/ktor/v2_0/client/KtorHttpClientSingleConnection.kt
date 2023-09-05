/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import kotlinx.coroutines.runBlocking

class KtorHttpClientSingleConnection(
  private val host: String,
  private val port: Int,
  private val installTracing: HttpClientConfig<*>.() -> Unit,
) : SingleConnection {

  private val client: HttpClient

  init {
    val engine = CIO.create {
      maxConnectionsCount = 1
    }

    client = HttpClient(engine) {
      installTracing()
    }
  }

  override fun doRequest(path: String, requestHeaders: MutableMap<String, String>) = runBlocking {
    val request = HttpRequestBuilder(
      scheme = "http",
      host = host,
      port = port,
      path = path,
    ).apply {
      requestHeaders.forEach { (name, value) -> headers.append(name, value) }
    }

    client.request(request).status.value
  }
}
