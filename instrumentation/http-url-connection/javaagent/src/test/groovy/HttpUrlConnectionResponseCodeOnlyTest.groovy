/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest

class HttpUrlConnectionResponseCodeOnlyTest extends HttpClientTest<HttpURLConnection> implements AgentTestTrait {

  @Override
  HttpURLConnection buildRequest(String method, URI uri, Map<String, String> headers) {
    return uri.toURL().openConnection() as HttpURLConnection
  }

  @Override
  int sendRequest(HttpURLConnection connection, String method, URI uri, Map<String, String> headers) {
    try {
      connection.setRequestMethod(method)
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      if (uri.toString().contains("/read-timeout")) {
        connection.readTimeout = READ_TIMEOUT_MS
      }
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
  Integer responseCodeOnRedirectError() {
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

  @Override
  boolean testReadTimeout() {
    true
  }
}
