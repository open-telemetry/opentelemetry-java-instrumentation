/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpRequest
import org.apache.http.protocol.BasicHttpContext
import spock.lang.Shared

import java.util.function.Consumer

abstract class ApacheHttpClientTest<T extends HttpRequest> extends HttpClientTest<T> {

  abstract protected CloseableHttpClient createClient()

  @Override
  Integer responseCodeOnRedirectError() {
    return 302
  }

  @Shared
  CloseableHttpClient client = createClient()

  @Override
  boolean testCausality() {
    false
  }

  @Override
  T buildRequest(String method, URI uri, Map<String, String> headers) {
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
  int sendRequest(T request, String method, URI uri, Map<String, String> headers) {
    def response = executeRequest(request, uri)
    response.entity?.content?.close() // Make sure the connection is closed.
    return response.statusLine.statusCode
  }

  // compilation fails with @Override annotation on this method (groovy quirk?)
  void sendRequestWithCallback(T request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    try {
      executeRequestWithCallback(request, uri) {
        it.entity?.content?.close() // Make sure the connection is closed.
        requestResult.complete(it.statusLine.statusCode)
      }
    } catch (Throwable throwable) {
      requestResult.complete(throwable)
    }
  }

  abstract T createRequest(String method, URI uri)

  abstract HttpResponse executeRequest(T request, URI uri)

  abstract void executeRequestWithCallback(T request, URI uri, Consumer<HttpResponse> callback)

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

abstract class AbstractApacheClientHostRequestTest extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    // also testing with an absolute path below
    return new BasicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request)
  }

  @Override
  void executeRequestWithCallback(BasicHttpRequest request, URI uri, Consumer<HttpResponse> callback) {
    client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request) {
      callback.accept(it)
    }
  }
}

abstract class AbstractApacheClientHostAbsoluteUriRequestTest extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    return new BasicHttpRequest(method, uri.toString())
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request)
  }

  @Override
  void executeRequestWithCallback(BasicHttpRequest request, URI uri, Consumer<HttpResponse> callback) {
    client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request) {
      callback.accept(it)
    }
  }
}

abstract class AbstractApacheClientHostRequestContextTest extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    // also testing with an absolute path below
    return new BasicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, new BasicHttpContext())
  }

  @Override
  void executeRequestWithCallback(BasicHttpRequest request, URI uri, Consumer<HttpResponse> callback) {
    client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, {
      callback.accept(it)
    }, new BasicHttpContext())
  }
}

abstract class AbstractApacheClientHostAbsoluteUriRequestContextTest extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    return new BasicHttpRequest(method, uri.toString())
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, new BasicHttpContext())
  }

  @Override
  void executeRequestWithCallback(BasicHttpRequest request, URI uri, Consumer<HttpResponse> callback) {
    client.execute(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), request, {
      callback.accept(it)
    }, new BasicHttpContext())
  }
}

abstract class AbstractApacheClientUriRequestTest extends ApacheHttpClientTest<HttpUriRequest> {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri) {
    return client.execute(request)
  }

  @Override
  void executeRequestWithCallback(HttpUriRequest request, URI uri, Consumer<HttpResponse> callback) {
    client.execute(request) {
      callback.accept(it)
    }
  }
}

abstract class AbstractApacheClientUriRequestContextTest extends ApacheHttpClientTest<HttpUriRequest> {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri) {
    return client.execute(request, new BasicHttpContext())
  }

  @Override
  void executeRequestWithCallback(HttpUriRequest request, URI uri, Consumer<HttpResponse> callback) {
    client.execute(request, {
      callback.accept(it)
    }, new BasicHttpContext())
  }
}