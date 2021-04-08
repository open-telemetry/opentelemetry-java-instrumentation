/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest

class HttpUrlConnectionResponseCodeOnlyTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    def request = buildRequest(uri)
    return sendRequest(request, method, headers)
  }

  private static HttpURLConnection buildRequest(URI uri) {
    return uri.toURL().openConnection() as HttpURLConnection
  }

  private static int sendRequest(HttpURLConnection connection, String method, Map<String, String> headers) {
    try {
      connection.setRequestMethod(method)
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      return connection.getResponseCode()
    } finally {
      connection.disconnect()
    }
  }

  @Override
  int maxRedirects() {
    20
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }

  @Override
  boolean testReusedRequest() {
    // HttpURLConnection can't be reused
    return false
  }

  @Override
  boolean testCallback() {
    return false
  }
}
