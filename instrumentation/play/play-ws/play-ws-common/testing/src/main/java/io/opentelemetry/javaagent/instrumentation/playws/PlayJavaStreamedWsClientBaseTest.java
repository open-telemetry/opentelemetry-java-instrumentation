/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSRequest;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.ahc.StandaloneAhcWSClient;

public class PlayJavaStreamedWsClientBaseTest extends PlayWsClientBaseTest<StandaloneWSRequest> {

  private static StandaloneWSClient wsClient;
  private static StandaloneWSClient wsClientWithReadTimeout;

  @BeforeAll
  static void setup() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer);
    wsClientWithReadTimeout =
        new StandaloneAhcWSClient(asyncHttpClientWithReadTimeout, materializer);
  }

  @AfterAll
  static void cleanup() throws IOException {
    wsClient.close();
    wsClientWithReadTimeout.close();
  }

  @Override
  public StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    StandaloneWSRequest request = getClient(uri).url(uri.toString()).setFollowRedirects(true);
    headers.forEach(request::addHeader);
    request.setMethod(method);
    return request;
  }

  @Override
  public int sendRequest(
      StandaloneWSRequest request, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    return internalSendRequest(request).toCompletableFuture().get().getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      StandaloneWSRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    internalSendRequest(request)
        .whenComplete(
            (response, throwable) -> {
              if (throwable != null) {
                requestResult.complete(throwable.getCause());
              } else {
                requestResult.complete(response.getStatus());
              }
            });
  }

  private static CompletionStage<StandaloneWSResponse> internalSendRequest(
      StandaloneWSRequest request) {
    CompletionStage<? extends StandaloneWSResponse> stream = request.stream();
    // The status can be ready before the body so explicitly call wait for body to be ready
    return stream
        .thenCompose(
            response -> response.getBodyAsSource().runFold("", (acc, out) -> "", materializer))
        .thenCombine(stream, (body, response) -> response);
  }

  private static StandaloneWSClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return wsClientWithReadTimeout;
    }
    return wsClient;
  }
}
