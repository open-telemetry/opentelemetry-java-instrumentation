/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client;

import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class VertxSingleConnection implements SingleConnection {

  private final HttpClient httpClient;
  private final String host;
  private final int port;

  public VertxSingleConnection(String host, int port) {
    this.host = host;
    this.port = port;
    HttpClientOptions clientOptions =
        new HttpClientOptions().setMaxPoolSize(1).setKeepAlive(true).setPipelining(true);
    httpClient = Vertx.vertx(new VertxOptions()).createHttpClient(clientOptions);
  }

  @Override
  public int doRequest(String path, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    String requestId = Objects.requireNonNull(headers.get(REQUEST_ID_HEADER));

    HttpClientRequest request = httpClient.request(HttpMethod.GET, port, host, path);
    headers.forEach(request::putHeader);

    CompletableFuture<HttpClientResponse> future = new CompletableFuture<>();
    request.handler(future::complete);

    request.end();
    HttpClientResponse response = future.get();
    String responseId = response.getHeader(REQUEST_ID_HEADER);
    if (!requestId.equals(responseId)) {
      throw new IllegalStateException(
          String.format("Received response with id %s, expected %s", responseId, requestId));
    }
    return response.statusCode();
  }
}
