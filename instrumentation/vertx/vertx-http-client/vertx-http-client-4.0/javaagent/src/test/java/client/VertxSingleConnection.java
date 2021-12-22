/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client;

import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import java.util.Map;
import java.util.Objects;
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
    RequestOptions requestOptions = new RequestOptions().setHost(host).setPort(port).setURI(path);
    headers.forEach(requestOptions::putHeader);
    Future<HttpClientRequest> request = httpClient.request(requestOptions);

    HttpClientResponse response =
        request.compose(req -> req.send()).toCompletionStage().toCompletableFuture().get();

    String responseId = response.getHeader(REQUEST_ID_HEADER);
    if (!requestId.equals(responseId)) {
      throw new IllegalStateException(
          String.format("Received response with id %s, expected %s", responseId, requestId));
    }
    return response.statusCode();
  }
}
