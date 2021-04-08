/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.squareup.okhttp.Callback
import com.squareup.okhttp.Headers
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import com.squareup.okhttp.internal.http.HttpMethod
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import spock.lang.Shared

class OkHttp2Test extends HttpClientTest implements AgentTestTrait {
  @Shared
  def client = new OkHttpClient()

  def setupSpec() {
    client.setConnectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    def request = buildRequest(method, uri, headers)
    return sendRequest(request)
  }

  @Override
  int doReusedRequest(String method, URI uri) {
    def request = buildRequest(method, uri, [:])
    sendRequest(request)
    return sendRequest(request)
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    client.newCall(buildRequest(method, uri, headers)).enqueue(new Callback() {
      @Override
      void onFailure(Request request, IOException e) {
        throw e
      }

      @Override
      void onResponse(Response response) throws IOException {
        callback.accept(response.code())
      }
    })
  }

  private static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    return new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(HeadersUtil.headersToArray(headers)))
      .build()
  }

  private int sendRequest(Request request) {
    return client.newCall(request).execute().code()
  }

  @Override
  boolean testRedirects() {
    false
  }
}
