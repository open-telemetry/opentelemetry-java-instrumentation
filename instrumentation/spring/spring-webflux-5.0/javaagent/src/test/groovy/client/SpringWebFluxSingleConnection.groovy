/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.netty.channel.ChannelOption
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import org.springframework.http.HttpMethod
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.ipc.netty.http.client.HttpClientOptions
import reactor.ipc.netty.resources.PoolResources
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.LoopResources

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class SpringWebFluxSingleConnection implements SingleConnection {
  private final ReactorClientHttpConnector connector
  private final WebClient webClient
  private final String host
  private final int port

  SpringWebFluxSingleConnection(boolean isOldVersion, String host, int port) {
    if (isOldVersion) {
      connector = new ReactorClientHttpConnector({ HttpClientOptions.Builder clientOptions ->
        clientOptions.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, HttpClientTest.CONNECT_TIMEOUT_MS)
        clientOptions.poolResources(PoolResources.fixed("pool", 1))
      })
    } else {
      def httpClient = HttpClient.create().tcpConfiguration({ tcpClient ->
        tcpClient.runOn(LoopResources.create("pool", 1, true))
        tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, HttpClientTest.CONNECT_TIMEOUT_MS)
      })
      connector = new ReactorClientHttpConnector(httpClient)
    }

    this.host = host
    this.port = port
    this.webClient = WebClient.builder().clientConnector(connector).build()
  }

  @Override
  int doRequest(String path, Map<String, String> headers) throws ExecutionException, InterruptedException, TimeoutException {
    String requestId = Objects.requireNonNull(headers.get(REQUEST_ID_HEADER))

    URI uri
    try {
      uri = new URL("http", host, port, path).toURI()
    } catch (MalformedURLException e) {
      throw new ExecutionException(e)
    }

    def request = webClient.method(HttpMethod.GET)
      .uri(uri)
      .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }

    def response = request.exchange().block()
    // read response body, this seems to be needed to ensure that the connection can be reused
    response.bodyToMono(String).block()

    String responseId = response.headers().asHttpHeaders().getFirst(REQUEST_ID_HEADER)
    if (requestId != responseId) {
      throw new IllegalStateException(
        String.format("Received response with id %s, expected %s", responseId, requestId))
    }

    return response.statusCode().value()
  }
}
