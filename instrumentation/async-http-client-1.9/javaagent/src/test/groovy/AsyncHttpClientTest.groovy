/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.Request
import com.ning.http.client.RequestBuilder
import com.ning.http.client.Response
import com.ning.http.client.uri.Uri
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import spock.lang.AutoCleanup
import spock.lang.Shared

class AsyncHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @AutoCleanup
  @Shared
  def client = new AsyncHttpClient()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
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
    // TODO(anuraaga): Do we also need to test ListenableFuture callback?
    client.executeRequest(buildRequest(method, uri, headers), new AsyncCompletionHandler<Void>() {
      @Override
      Void onCompleted(Response response) throws Exception {
        callback.accept(response.statusCode)
        return null
      }
    })
  }

  private static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def requestBuilder = new RequestBuilder(method)
      .setUri(Uri.create(uri.toString()))
    headers.entrySet().each {
      requestBuilder.setHeader(it.key, it.value)
    }
    return requestBuilder.build()
  }

  private int sendRequest(Request request) {
    return client.executeRequest(request).get().statusCode
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}


