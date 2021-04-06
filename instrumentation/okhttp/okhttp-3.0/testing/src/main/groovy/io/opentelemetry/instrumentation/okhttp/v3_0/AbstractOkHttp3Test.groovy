/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0

import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.http.HttpMethod
import spock.lang.Shared

abstract class AbstractOkHttp3Test extends HttpClientTest {

  abstract OkHttpClient.Builder configureClient(OkHttpClient.Builder clientBuilder)

  @Shared
  def client = configureClient(
    new OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(false))
    .build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    def request = buildRequest(method, uri, headers)
    def response = client.newCall(request).execute()
    return response.code()
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    def request = buildRequest(method, uri, headers)
    client.newCall(request).enqueue(new Callback() {
      @Override
      void onFailure(Call call, IOException e) {
        throw e
      }

      @Override
      void onResponse(Call call, Response response) throws IOException {
        callback.accept(response.code())
      }
    })
  }

  private static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    return new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(headers)).build()
  }

  boolean testRedirects() {
    false
  }

  @Override
  boolean testCausality() {
    false
  }

}
