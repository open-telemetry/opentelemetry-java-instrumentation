/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.http.HttpMethod
import spock.lang.Shared

import java.util.concurrent.TimeUnit

abstract class AbstractOkHttp3Test extends HttpClientTest<Request> {

  abstract Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder)

  @Shared
  Call.Factory client = createCallFactory(
    new OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .protocols(Arrays.asList(Protocol.HTTP_1_1))
      .retryOnConnectionFailure(false))

  @Override
  Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    return new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(headers)).build()
  }

  @Override
  int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
    def response = client.newCall(request).execute()
    response.body().withCloseable {
      return response.code()
    }
  }

  @Override
  void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    client.newCall(request).enqueue(new Callback() {
      @Override
      void onFailure(Call call, IOException e) {
        requestResult.complete(e)
      }

      @Override
      void onResponse(Call call, Response response) throws IOException {
        response.body().withCloseable {
          requestResult.complete(response.code())
        }
      }
    })
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_HOST,
      SemanticAttributes.HTTP_SCHEME
    ]
    def attributes = super.httpAttributes(uri) + extra

    // flavor is extracted from the response, and those URLs cause exceptions (= null response)
    switch (uri.toString()) {
      case "http://localhost:61/":
      case "https://192.0.2.1/":
        attributes.remove(SemanticAttributes.HTTP_FLAVOR)
    }

    attributes
  }
}
