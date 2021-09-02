/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CancellationException

abstract class ApacheHttpAsyncClientTest extends HttpClientTest<HttpUriRequest> implements AgentTestTrait {

  @Shared
  RequestConfig requestConfig = RequestConfig.custom()
    .setConnectTimeout(CONNECT_TIMEOUT_MS)
    .build()

  @AutoCleanup
  @Shared
  def client = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build()

  def setupSpec() {
    client.start()
  }

  @Override
  Integer responseCodeOnRedirectError() {
    return 302
  }

  @Override
  HttpUriRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = createRequest(method, uri)
    headers.entrySet().each {
      request.setHeader(new BasicHeader(it.key, it.value))
    }
    return request
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET
    ]
    super.httpAttributes(uri) + extra
  }

  // compilation fails with @Override annotation on this method (groovy quirk?)
  int sendRequest(HttpUriRequest request, String method, URI uri, Map<String, String> headers) {
    def response = executeRequest(request, uri)
    response.entity?.content?.close() // Make sure the connection is closed.
    return response.statusLine.statusCode
  }

  // compilation fails with @Override annotation on this method (groovy quirk?)
  void sendRequestWithCallback(HttpUriRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    try {
      executeRequestWithCallback(request, uri, new FutureCallback<HttpResponse>() {
        @Override
        void completed(HttpResponse httpResponse) {
          httpResponse.entity?.content?.close() // Make sure the connection is closed.
          requestResult.complete(httpResponse.statusLine.statusCode)
        }

        @Override
        void failed(Exception e) {
          requestResult.complete(e)
        }

        @Override
        void cancelled() {
          requestResult.complete(new CancellationException())
        }
      })
    } catch (Throwable throwable) {
      requestResult.complete(throwable)
    }
  }

  abstract HttpUriRequest createRequest(String method, URI uri)

  abstract HttpResponse executeRequest(HttpUriRequest request, URI uri)

  abstract void executeRequestWithCallback(HttpUriRequest request, URI uri, FutureCallback<HttpResponse> callback)

  static String fullPathFromURI(URI uri) {
    StringBuilder builder = new StringBuilder()
    if (uri.getPath() != null) {
      builder.append(uri.getPath())
    }

    if (uri.getQuery() != null) {
      builder.append('?')
      builder.append(uri.getQuery())
    }

    if (uri.getFragment() != null) {
      builder.append('#')
      builder.append(uri.getFragment())
    }
    return builder.toString()
  }
}

class ApacheClientUriRequest extends ApacheHttpAsyncClientTest {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri) {
    return client.execute(request, null).get()
  }

  @Override
  void executeRequestWithCallback(HttpUriRequest request, URI uri, FutureCallback<HttpResponse> callback) {
    client.execute(request, callback)
  }
}

class ApacheClientHostRequest extends ApacheHttpAsyncClientTest {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    // also testing with absolute path below
    return new HttpUriRequest(method, new URI(fullPathFromURI(uri)))
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, null).get()
  }

  @Override
  void executeRequestWithCallback(HttpUriRequest request, URI uri, FutureCallback<HttpResponse> callback) {
    client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, callback)
  }
}

class ApacheClientHostAbsoluteUriRequest extends ApacheHttpAsyncClientTest {

  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, new URI(uri.toString()))
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, null).get()
  }

  @Override
  void executeRequestWithCallback(HttpUriRequest request, URI uri, FutureCallback<HttpResponse> callback) {
    client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, callback)
  }
}
