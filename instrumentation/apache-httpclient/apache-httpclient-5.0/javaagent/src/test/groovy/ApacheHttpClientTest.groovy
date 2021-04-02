/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.message.BasicClassicHttpRequest
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.protocol.BasicHttpContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Timeout

abstract class ApacheHttpClientTest<T extends HttpRequest> extends HttpClientTest implements AgentTestTrait {
  @Shared
  @AutoCleanup
  CloseableHttpClient client

  def setupSpec() {
    HttpClientBuilder builder = HttpClients.custom()
    builder.setDefaultRequestConfig(RequestConfig.custom()
      .setConnectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .build())

    client = builder.build()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    def request = createRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def response = executeRequest(request, uri)
    response.close() // Make sure the connection is closed.

    return response.code
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    def request = createRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    executeRequestAsync(request, uri) {
      it.close() // Make sure the connection is closed.
      callback.accept(it.code)
    }
  }

  abstract T createRequest(String method, URI uri)

  abstract ClassicHttpResponse executeRequest(T request, URI uri)

  abstract void executeRequestAsync(T request, URI uri, Consumer<ClassicHttpResponse> callback)

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
class ApacheClientHostRequest extends ApacheHttpClientTest<ClassicHttpRequest> {
  @Override
  ClassicHttpRequest createRequest(String method, URI uri) {
    return new BasicClassicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort()), request)
  }

  @Override
  void executeRequestAsync(ClassicHttpRequest request, URI uri, Consumer<ClassicHttpResponse> callback) {
    // This is not actually an asynchronous invocation since the response handler is always invoked
    // inline. But context propagation works the same for a response handler as a callback so we
    // treat it as an async test.
    client.execute(new HttpHost(uri.getHost(), uri.getPort()), request) {
      callback.accept(it)
    }
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}

@Timeout(5)
class ApacheClientHostRequestContext extends ApacheHttpClientTest<ClassicHttpRequest> {
  @Override
  ClassicHttpRequest createRequest(String method, URI uri) {
    return new BasicClassicHttpRequest(method, fullPathFromURI(uri))
  }

  @Override
  ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) {
    return client.execute(new HttpHost(uri.getHost(), uri.getPort()), request, new BasicHttpContext())
  }

  @Override
  void executeRequestAsync(ClassicHttpRequest request, URI uri, Consumer<ClassicHttpResponse> callback) {
    // This is not actually an asynchronous invocation since the response handler is always invoked
    // inline. But context propagation works the same for a response handler as a callback so we
    // treat it as an async test.
    client.execute(new HttpHost(uri.getHost(), uri.getPort()), request, new BasicHttpContext()) {
      callback.accept(it)
    }
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}

@Timeout(5)
class ApacheClientUriRequest extends ApacheHttpClientTest<ClassicHttpRequest> {
  @Override
  ClassicHttpRequest createRequest(String method, URI uri) {
    return new HttpUriRequestBase(method, uri)
  }

  @Override
  ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) {
    return client.execute(request)
  }

  @Override
  void executeRequestAsync(ClassicHttpRequest request, URI uri, Consumer<ClassicHttpResponse> callback) {
    // This is not actually an asynchronous invocation since the response handler is always invoked
    // inline. But context propagation works the same for a response handler as a callback so we
    // treat it as an async test.
    client.execute(request) {
      callback.accept(it)
    }
  }
}

@Timeout(5)
class ApacheClientUriRequestContext extends ApacheHttpClientTest<ClassicHttpRequest> {
  @Override
  ClassicHttpRequest createRequest(String method, URI uri) {
    return new HttpUriRequestBase(method, uri)
  }

  @Override
  ClassicHttpResponse executeRequest(ClassicHttpRequest request, URI uri) {
    return client.execute(request, new BasicHttpContext())
  }

  @Override
  void executeRequestAsync(ClassicHttpRequest request, URI uri, Consumer<ClassicHttpResponse> callback) {
    // This is not actually an asynchronous invocation since the response handler is always invoked
    // inline. But context propagation works the same for a response handler as a callback so we
    // treat it as an async test.
    client.execute(request, new BasicHttpContext()) {
      callback.accept(it)
    }
  }
}
