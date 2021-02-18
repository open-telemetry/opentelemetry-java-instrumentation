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
import spock.lang.AutoCleanup
import spock.lang.Shared

class AsyncHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @AutoCleanup
  @Shared
  def client = new AsyncHttpClient()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {

    RequestBuilder requestBuilder = new RequestBuilder(method)
      .setUri(Uri.create(uri.toString()))
    headers.entrySet().each {
      requestBuilder.addHeader(it.key, it.value)
    }
    Request request = requestBuilder.build()

    def handler = new AsyncCompletionHandler() {
      @Override
      Object onCompleted(Response response) throws Exception {
        if (callback != null) {
          callback()
        }
        return response
      }
    }

    def response = client.executeRequest(request, handler).get()
    response.statusCode
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


