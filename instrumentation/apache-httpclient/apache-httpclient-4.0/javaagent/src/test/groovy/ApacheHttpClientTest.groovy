/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpRequest
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.protocol.BasicHttpContext
import spock.lang.Shared
import spock.lang.Timeout

abstract class ApacheHttpClientTest<T extends HttpRequest> extends HttpClientTest implements AgentTestTrait {
  @Shared
  def client = new DefaultHttpClient()

  def setupSpec() {
    HttpParams httpParams = client.getParams()
    HttpConnectionParams.setConnectionTimeout(httpParams, CONNECT_TIMEOUT_MS)
  }

  @Override
  boolean testCausality() {
    return false
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = createRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def response = executeRequest(request, uri, callback)
    response.entity?.content?.close() // Make sure the connection is closed.

    return response.statusLine.statusCode
  }

  abstract T createRequest(String method, URI uri)

  abstract HttpResponse executeRequest(T request, URI uri, Closure callback)

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

@Timeout(5)
class ApacheClientHostRequest extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    return new BasicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri, Closure callback) {
    def response = client.execute(new HttpHost(uri.getHost(), uri.getPort()), request)
    callback?.call()
    return response
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}

@Timeout(5)
class ApacheClientHostRequestContext extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    return new BasicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri, Closure callback) {
    def response = client.execute(new HttpHost(uri.getHost(), uri.getPort()), request, new BasicHttpContext())
    callback?.call()
    return response
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}

@Timeout(5)
class ApacheClientHostRequestResponseHandler extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    return new BasicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri, Closure callback) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort()), request, {
      callback?.call()
      return it
    })
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}

@Timeout(5)
class ApacheClientHostRequestResponseHandlerContext extends ApacheHttpClientTest<BasicHttpRequest> {
  @Override
  BasicHttpRequest createRequest(String method, URI uri) {
    return new BasicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  HttpResponse executeRequest(BasicHttpRequest request, URI uri, Closure callback) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort()), request, {
      callback?.call()
      return it
    }, new BasicHttpContext())
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}

@Timeout(5)
class ApacheClientUriRequest extends ApacheHttpClientTest<HttpUriRequest> {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri, Closure callback) {
    def response = client.execute(request)
    callback?.call()
    return response
  }
}

@Timeout(5)
class ApacheClientUriRequestContext extends ApacheHttpClientTest<HttpUriRequest> {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri, Closure callback) {
    def response = client.execute(request, new BasicHttpContext())
    callback?.call()
    return response
  }
}

@Timeout(5)
class ApacheClientUriRequestResponseHandler extends ApacheHttpClientTest<HttpUriRequest> {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri, Closure callback) {
    return client.execute(request, {
      callback?.call()
      it
    })
  }
}

@Timeout(5)
class ApacheClientUriRequestResponseHandlerContext extends ApacheHttpClientTest<HttpUriRequest> {
  @Override
  HttpUriRequest createRequest(String method, URI uri) {
    return new HttpUriRequest(method, uri)
  }

  @Override
  HttpResponse executeRequest(HttpUriRequest request, URI uri, Closure callback) {
    return client.execute(request, {
      callback?.call()
      it
    }, new BasicHttpContext())
  }
}
