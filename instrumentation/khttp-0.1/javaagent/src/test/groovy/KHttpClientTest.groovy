/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import khttp.KHttp

class KHttpClientTest extends HttpClientTest<Void> implements AgentTestTrait {

  @Override
  Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null
  }

  @Override
  int sendRequest(Void request, String method, URI uri, Map<String, String> headers) {
    headers.put("User-Agent", "khttp")
    // khttp applies the same timeout for both connect and read
    def timeoutSeconds = CONNECT_TIMEOUT_MS / 1000
    def response = KHttp.request(method, uri.toString(), headers, Collections.emptyMap(), null, null, null, null, timeoutSeconds)
    return response.statusCode
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }

  @Override
  boolean testReusedRequest() {
    // these tests will pass, but they don't really test anything since REQUEST is Void
    false
  }

  @Override
  boolean testCallback() {
    return false
  }

  @Override
  String userAgent() {
    return "khttp"
  }
}
