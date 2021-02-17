/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.internal.http.HttpMethod
import spock.lang.Timeout

@Timeout(5)
class OkHttp3Test extends HttpClientTest implements AgentTestTrait {

  def client = new OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(false)
    .build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    def request = new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(headers)).build()
    def response = client.newCall(request).execute()
    callback?.call()
    return response.code()
  }

  boolean testRedirects() {
    false
  }

  @Override
  boolean testCausality() {
    false
  }

}
