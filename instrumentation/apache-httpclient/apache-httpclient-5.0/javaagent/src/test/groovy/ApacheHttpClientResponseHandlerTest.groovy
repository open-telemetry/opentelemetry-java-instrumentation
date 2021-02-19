/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.protocol.BasicHttpContext
import spock.lang.AutoCleanup
import spock.lang.Shared

abstract class ApacheHttpClientResponseHandlerTest<T extends HttpRequest> extends HttpClientTest implements AgentTestTrait {

  @Shared
  @AutoCleanup
  def client

  @Shared
  def handler = new HttpClientResponseHandler<Integer>() {
    @Override
    Integer handleResponse(ClassicHttpResponse response) {
      return response.code
    }
  }

  def setupSpec() {
    HttpClientBuilder builder = HttpClients.custom()
    builder.setDefaultRequestConfig(RequestConfig.custom()
      .setConnectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .build())

    client = builder.build()
  }

  abstract int executeRequest(T request)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = new HttpUriRequestBase(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def status = executeRequest(request)

    // handler execution is included within the client span, so we can't call the callback there.
    callback?.call()

    return status
  }
}

class ApacheClientHandlerRequest extends ApacheHttpClientResponseHandlerTest<ClassicHttpRequest> {

  @Override
  int executeRequest(ClassicHttpRequest request) {
    return client.execute(request, handler)
  }
}

class ApacheClientContextHandlerRequest extends ApacheHttpClientResponseHandlerTest<ClassicHttpRequest> {

  @Override
  int executeRequest(ClassicHttpRequest request) {
    return client.execute(request, new BasicHttpContext(), handler)
  }
}

class ApacheClientHostHandlerRequest extends ApacheHttpClientResponseHandlerTest<ClassicHttpRequest> {

  @Override
  int executeRequest(ClassicHttpRequest request) {
    URI uri = request.getUri()
    return client.execute(new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort()), request, handler)
  }
}

class ApacheClientHostAndContextHandlerRequest extends ApacheHttpClientResponseHandlerTest<ClassicHttpRequest> {

  @Override
  int executeRequest(ClassicHttpRequest request) {
    URI uri = request.getUri()
    return client.execute(new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort()), request, new BasicHttpContext(), handler)
  }
}
