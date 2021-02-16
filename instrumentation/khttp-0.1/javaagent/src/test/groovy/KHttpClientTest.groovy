/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import khttp.KHttp

class KHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    headers.put("User-Agent", "khttp")
    // khttp applies the same timeout for both connect and read
    def timeoutSeconds = CONNECT_TIMEOUT_MS / 1000
    def response = KHttp.request(method, uri.toString(), headers, Collections.emptyMap(), null, null, null, null, timeoutSeconds)
    if (callback != null) {
      callback.call()
    }
    return response.statusCode
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }

  @Override
  String userAgent() {
    return "khttp"
  }
}
