/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.Request
import com.ning.http.client.RequestBuilder
import com.ning.http.client.Response
import com.ning.http.client.uri.Uri
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.SingleConnection
import spock.lang.AutoCleanup
import spock.lang.Shared

class AsyncHttpClientTest extends HttpClientTest<Request> implements AgentTestTrait {

  @AutoCleanup
  @Shared
  def client = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
    .setConnectTimeout(CONNECT_TIMEOUT_MS).build())

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
    // TODO(anuraaga): Do we also need to test ListenableFuture callback?
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

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    // AsyncHttpClient does not support HTTP 1.1 pipelining nor waiting for connection pool slots to
    // free up (it immediately throws "Too many connections" IOException). Therefore making a single
    // connection test would require manually sequencing the connections, which is not meaningful
    // for a high concurrency test.
    return null
  }
}


