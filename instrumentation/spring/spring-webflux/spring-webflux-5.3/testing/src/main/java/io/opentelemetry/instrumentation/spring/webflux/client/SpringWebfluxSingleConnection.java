/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

final class SpringWebfluxSingleConnection implements SingleConnection {

  private final String host;
  private final int port;
  private final WebClient webClient;

  public SpringWebfluxSingleConnection(
      String host, int port, UnaryOperator<WebClient.Builder> instrumentationFunction) {
    this.host = host;
    this.port = port;
    this.webClient =
        instrumentationFunction
            .apply(
                WebClient.builder()
                    .clientConnector(ClientHttpConnectorFactory.createSingleConnection()))
            .build();
  }

  @Override
  public int doRequest(String path, Map<String, String> headers) throws Exception {
    String requestId = Objects.requireNonNull(headers.get(REQUEST_ID_HEADER));

    URI uri;
    try {
      uri = new URL("http", host, port, path).toURI();
    } catch (MalformedURLException e) {
      throw new ExecutionException(e);
    }

    WebClient.RequestBodySpec request =
        webClient.method(HttpMethod.GET).uri(uri).headers(h -> headers.forEach(h::add));

    if (Webflux7Util.isWebflux7) {
      return Webflux7Util.doRequest(
          request,
          response -> {
            String responseId = response.headers().asHttpHeaders().getFirst(REQUEST_ID_HEADER);
            if (!requestId.equals(responseId)) {
              return Mono.error(
                  new IllegalStateException(
                      String.format(
                          "Received response with id %s, expected %s", responseId, requestId)));
            }
            return Mono.just(Webflux7Util.getStatusCode(response));
          });
    } else {
      ClientResponse response = request.exchange().block();
      // read response body, this seems to be needed to ensure that the connection can be reused
      response.bodyToMono(String.class).block();

      String responseId = response.headers().asHttpHeaders().getFirst(REQUEST_ID_HEADER);
      if (!requestId.equals(responseId)) {
        throw new IllegalStateException(
            String.format("Received response with id %s, expected %s", responseId, requestId));
      }

      return response.statusCode().value();
    }
  }
}
