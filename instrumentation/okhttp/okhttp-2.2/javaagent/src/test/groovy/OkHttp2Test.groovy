/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.squareup.okhttp.Headers
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.internal.http.HttpMethod
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class OkHttp2Test extends HttpClientTest implements AgentTestTrait {
  @Shared
  def client = new OkHttpClient()

  def setupSpec() {
    client.setConnectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null

    def request = new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(HeadersUtil.headersToArray(headers)))
      .build()
    def response = client.newCall(request).execute()
    callback?.call()
    return response.code()
  }

  boolean testRedirects() {
    false
  }
}
