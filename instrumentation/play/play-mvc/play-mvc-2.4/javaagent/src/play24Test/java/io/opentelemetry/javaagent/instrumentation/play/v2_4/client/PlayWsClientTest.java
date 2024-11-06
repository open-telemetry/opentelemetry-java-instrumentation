/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4.client;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

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
    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(NETWORK_PROTOCOL_VERSION);
          return attributes;
        });

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
    CompletableFuture<WSResponse> result = new CompletableFuture<>();
    F.Promise<WSResponse> promise = wsRequest.execute(method);
    promise.onRedeem(result::complete);
    promise.onFailure(result::completeExceptionally);
    return result;
  }
}
