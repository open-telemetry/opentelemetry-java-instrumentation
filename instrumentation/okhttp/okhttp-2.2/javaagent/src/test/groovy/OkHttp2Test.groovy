/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.squareup.okhttp.Callback
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import com.squareup.okhttp.internal.http.HttpMethod
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class OkHttp2Test extends HttpClientTest<Request> implements AgentTestTrait {
  @Shared
  def client = new OkHttpClient()

  def setupSpec() {
    client.setConnectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  @Override
  Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    def request = new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
    headers.forEach({ key, value -> request.header(key, value) })
    return request.build()
  }

  @Override
  int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
    return client.newCall(request).execute().code()
  }

  @Override
  void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    client.newCall(request).enqueue(new Callback() {
      @Override
      void onFailure(Request req, IOException e) {
        requestResult.complete(e)
      }

      @Override
      void onResponse(Response response) throws IOException {
        requestResult.complete(response.code())
      }
    })
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

  @Override
  boolean testRedirects() {
    false
  }
}
