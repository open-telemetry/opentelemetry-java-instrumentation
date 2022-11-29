/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.netty.channel.ChannelOption
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.http.HttpMethod
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class SpringWebfluxHttpClientTest extends HttpClientTest<WebClient.RequestBodySpec> implements AgentTestTrait {

  @Override
  WebClient.RequestBodySpec buildRequest(String method, URI uri, Map<String, String> headers) {
    def connector
    if (isOldVersion()) {
      connector = new ReactorClientHttpConnector({ clientOptions ->
        clientOptions.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
      })
    } else {
      def httpClient = reactor.netty.http.client.HttpClient.create().tcpConfiguration({ tcpClient ->
        tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
      })
      connector = new ReactorClientHttpConnector(httpClient)
    }
    return WebClient.builder()
      .filter(ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          return Mono.error(new TestException(clientResponse.statusCode().value()))
        }))
      .clientConnector(connector).build().method(HttpMethod.resolve(method))
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
  }

  private static boolean isOldVersion() {
    try {
      Class.forName("reactor.netty.http.client.HttpClient")
      return false
    } catch (ClassNotFoundException exception) {
      return true
    }
  }

  @Override
  int sendRequest(WebClient.RequestBodySpec request, String method, URI uri, Map<String, String> headers) {
    try {
      return request.exchange().block().statusCode().value()
    } catch (TestException e) {
      return e.getStatusCode()
    }
  }

  @Override
  void sendRequestWithCallback(WebClient.RequestBodySpec request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    request.exchange().subscribe({
      requestResult.complete(it.statusCode().value())
    }, {
      requestResult.complete(it)
    })
  }

  @Override
  Throwable clientSpanError(URI uri, Throwable exception) {
    if (!exception.getClass().getName().endsWith("WebClientRequestException")) {
      switch (uri.toString()) {
        case "http://localhost:61/": // unopened port
          if (!exception.getClass().getName().endsWith("AnnotatedConnectException")) {
            exception = exception.getCause()
          }
          break
        case "https://192.0.2.1/": // non routable address
          exception = exception.getCause()
      }
    }
    return exception
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    def attributes = super.httpAttributes(uri)
    attributes.remove(SemanticAttributes.HTTP_FLAVOR)
    return attributes
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    return new SpringWebFluxSingleConnection(isOldVersion(), host, port)
  }

  static class TestException extends RuntimeException {

    int statusCode

    TestException(int statusCode) {
      this.statusCode = statusCode
    }
  }
}
