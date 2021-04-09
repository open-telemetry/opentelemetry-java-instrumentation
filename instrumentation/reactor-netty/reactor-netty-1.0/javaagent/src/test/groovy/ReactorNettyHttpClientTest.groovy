/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.base.SingleConnection
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import reactor.netty.http.client.HttpClient

class ReactorNettyHttpClientTest extends AbstractReactorNettyHttpClientTest {

  HttpClient createHttpClient() {
    return HttpClient.create()
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    def httpClient = HttpClient
      .newConnection()
      .host(host)
      .port(port)

    return new SingleConnection() {

      @Override
      int doRequest(String path, Map<String, String> headers) throws ExecutionException, InterruptedException, TimeoutException {
        return httpClient
          .headers({ h -> headers.each { k, v -> h.add(k, v) } })
          .get()
          .uri(path)
          .response()
          .block()
          .status().code()
      }
    }
  }
}
