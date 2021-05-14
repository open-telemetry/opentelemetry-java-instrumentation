/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.Dsl
import org.asynchttpclient.Request
import org.asynchttpclient.RequestBuilder
import org.asynchttpclient.Response
import org.asynchttpclient.uri.Uri
import spock.lang.AutoCleanup
import spock.lang.Shared

class AsyncHttpClientTest extends HttpClientTest<Request> implements AgentTestTrait {

  @AutoCleanup
  @Shared
  def client = Dsl.asyncHttpClient(Dsl.config().setConnectTimeout(CONNECT_TIMEOUT_MS))

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
  void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {
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
  boolean testHttps() {
    false
  }
}


