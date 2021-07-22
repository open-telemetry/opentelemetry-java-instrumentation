/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.Dsl
import org.asynchttpclient.Request
import org.asynchttpclient.RequestBuilder
import org.asynchttpclient.Response
import org.asynchttpclient.uri.Uri
import spock.lang.AutoCleanup
import spock.lang.Shared

class AsyncHttpClientTest extends HttpClientTest<Request> implements AgentTestTrait {

  // request timeout is needed in addition to connect timeout on async-http-client versions 2.1.0+
  @AutoCleanup
  @Shared
  def client = Dsl.asyncHttpClient(Dsl.config().setConnectTimeout(CONNECT_TIMEOUT_MS)
    .setRequestTimeout(CONNECT_TIMEOUT_MS))

  @Override
  Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def requestBuilder = new RequestBuilder(method)
      .setUri(Uri.create(uri.toString()))
    headers.entrySet().each {
      requestBuilder.setHeader(it.key, it.value)
    }
    return requestBuilder.build()
  }

  @Override
  int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
    return client.executeRequest(request).get().statusCode
  }

  @Override
  void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    client.executeRequest(request, new AsyncCompletionHandler<Void>() {
      @Override
      Void onCompleted(Response response) throws Exception {
        requestResult.complete(response.statusCode)
        return null
      }

      @Override
      void onThrowable(Throwable throwable) {
        requestResult.complete(throwable)
      }
    })
  }

  //TODO see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2347
//  @Override
//  String userAgent() {
//    return "AHC"
//  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET
    ]
    super.httpAttributes(uri) + extra
  }
}
