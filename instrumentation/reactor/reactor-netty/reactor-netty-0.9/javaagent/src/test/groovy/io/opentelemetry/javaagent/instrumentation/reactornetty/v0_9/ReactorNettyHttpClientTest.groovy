/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9

import io.netty.channel.ChannelOption
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import reactor.netty.http.client.HttpClient

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class ReactorNettyHttpClientTest extends AbstractReactorNettyHttpClientTest {

  HttpClient createHttpClient() {
    return HttpClient.create().tcpConfiguration({ tcpClient ->
      tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
    })
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    String url
    try {
      url = new URL("http", host, port, "").toString()
    } catch (MalformedURLException e) {
      throw new ExecutionException(e)
    }

    def httpClient = HttpClient
      .newConnection()
      .baseUrl(url)

    return new SingleConnection() {

      @Override
      int doRequest(String path, Map<String, String> headers) throws ExecutionException, InterruptedException, TimeoutException {
        return httpClient
          .headers({ h -> headers.each { k, v -> h.add(k, v) } })
          .get()
          .uri(path)
          .responseSingle { resp, content ->
            // Make sure to consume content since that's when we close the span.
            content.map { resp }
          }
          .block().status().code()
      }
    }
  }
}
