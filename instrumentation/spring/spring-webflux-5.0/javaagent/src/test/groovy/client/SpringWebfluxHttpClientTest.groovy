/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.netty.channel.ChannelOption
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import org.springframework.http.HttpMethod
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient

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
    return WebClient.builder().clientConnector(connector).build().method(HttpMethod.resolve(method))
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
    return request.exchange().block().statusCode().value()
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
  SingleConnection createSingleConnection(String host, int port) {
    return new SpringWebFluxSingleConnection(isOldVersion(), host, port)
  }
}
