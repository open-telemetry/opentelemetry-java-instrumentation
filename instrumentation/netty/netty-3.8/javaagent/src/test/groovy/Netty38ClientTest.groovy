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
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.nio.channels.ClosedChannelException

class Netty38ClientTest extends HttpClientTest<Request> implements AgentTestTrait {

  @Shared
  @AutoCleanup
  AsyncHttpClient client = new AsyncHttpClient(getClientConfig())

  def getClientConfig() {
    def builder = new AsyncHttpClientConfig.Builder()
      .setUserAgent("test-user-agent")

    if (builder.metaClass.getMetaMethod("setConnectTimeout", int) != null) {
      builder.setConnectTimeout(CONNECT_TIMEOUT_MS)
    } else {
      builder.setRequestTimeoutInMs(CONNECT_TIMEOUT_MS)
    }
    if (builder.metaClass.getMetaMethod("setFollowRedirect", boolean) != null) {
      builder.setFollowRedirect(true)
    } else {
      builder.setFollowRedirects(true)
    }
    if (builder.metaClass.getMetaMethod("setMaxRedirects", int) != null) {
      builder.setMaxRedirects(3)
    } else {
      builder.setMaximumNumberOfRedirects(3)
    }

    return builder.build()
  }

  @Override
  Request buildRequest(String method, URI uri, Map<String, String> headers) {
    def requestBuilder = new RequestBuilder(method)
      .setUrl(uri.toString())
    headers.entrySet().each {
      requestBuilder.addHeader(it.key, it.value)
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
  String userAgent() {
    return "test-user-agent"
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  Throwable clientSpanError(URI uri, Throwable exception) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
        exception = exception.getCause() != null ? exception.getCause() : new ConnectException("Connection refused: localhost/127.0.0.1:61")
        break
      case "http://192.0.2.1/": // non routable address
        exception = exception.getCause() != null ? exception.getCause() : new ClosedChannelException()
    }
    return exception
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return []
    }
    return super.httpAttributes(uri)
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testHttps() {
    false
  }
}
