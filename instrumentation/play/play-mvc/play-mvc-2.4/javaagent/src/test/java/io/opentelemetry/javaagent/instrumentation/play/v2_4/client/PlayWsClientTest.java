/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4.client;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import play.libs.ws.WS;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

// Play 2.6+ uses a separately versioned client that shades the underlying dependency
// This means our built in instrumentation won't work.
class PlayWsClientTest extends AbstractHttpClientTest<WSRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  private static WSClient wsClient;

  @BeforeEach
  void setUp() {
    wsClient = WS.newClient(-1);
    autoCleanup.deferCleanup(wsClient);
  }

  @Override
  public WSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    WSRequest request = wsClient.url(uri.toString());
    headers.forEach(request::setHeader);
    return request;
  }

  @Override
  public int sendRequest(WSRequest wsRequest, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    return internalSendRequest(wsRequest, method).toCompletableFuture().get().getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      WSRequest wsRequest,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    internalSendRequest(wsRequest, method)
        .whenComplete(
            (wsResponse, throwable) -> {
              if (wsResponse != null) {
                httpClientResult.complete(wsResponse::getStatus, throwable);
              } else {
                httpClientResult.complete(
                    () -> {
                      throw new IllegalArgumentException("wsResponse is null!", throwable);
                    },
                    throwable);
              }
            });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.setTestRedirects(false);
    optionsBuilder.setTestReadTimeout(false);
    optionsBuilder.spanEndsAfterBody();

    // Play HTTP client uses AsyncHttpClient internally which does not support HTTP 1.1 pipelining
    // nor waiting for connection pool slots to free up. Therefore making a single connection test
    // would require manually sequencing the connections, which is not meaningful for a high
    // concurrency test.
    optionsBuilder.setSingleConnectionFactory(
        (a, b) ->
            null); // this can be omitted as it's the default. it's here for the comment above.
  }

  private static CompletionStage<WSResponse> internalSendRequest(
      WSRequest wsRequest, String method) {
    return wsRequest.execute(method);
  }
}
