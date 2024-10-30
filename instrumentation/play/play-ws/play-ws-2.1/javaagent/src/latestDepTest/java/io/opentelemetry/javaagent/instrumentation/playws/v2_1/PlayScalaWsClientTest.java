/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_1;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import play.api.libs.ws.StandaloneWSClient;
import play.api.libs.ws.StandaloneWSRequest;
import play.api.libs.ws.StandaloneWSResponse;
import play.api.libs.ws.ahc.StandaloneAhcWSClient;
import scala.Function1;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.jdk.javaapi.CollectionConverters;
import scala.util.Try;

class PlayScalaWsClientTest extends PlayWsClientBaseTest<StandaloneWSRequest> {

  private static StandaloneWSClient wsClient;
  private static StandaloneWSClient wsClientWithReadTimeout;

  @BeforeAll
  void setup() {
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
  public StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers)
      throws MalformedURLException {
    return getClient(uri)
        .url(uri.toURL().toString())
        .withMethod(method)
        .withFollowRedirects(true)
        .withHttpHeaders(CollectionConverters.asScala(headers).toSeq());
  }

  @Override
  public int sendRequest(
      StandaloneWSRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    Future<StandaloneWSResponse> futureResponse = request.execute();
    Await.ready(futureResponse, Duration.apply(10, TimeUnit.SECONDS));
    Try<StandaloneWSResponse> value = futureResponse.value().get();
    if (value.isSuccess()) {
      return value.get().status();
    }
    // Catch the Throwable and rethrow it
    throw (Exception) value.failed().get();
  }

  @Override
  public void sendRequestWithCallback(
      StandaloneWSRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    request
        .execute()
        .onComplete(
            new Function1<Try<StandaloneWSResponse>, Void>() {
              @Override
              public Void apply(Try<StandaloneWSResponse> response) {
                if (response.isSuccess()) {
                  requestResult.complete(response.get().status());
                } else {
                  requestResult.complete(response.failed().get());
                }
                return null;
              }
            },
            ExecutionContext.global());
  }

  private static StandaloneWSClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return wsClientWithReadTimeout;
    }
    return wsClient;
  }
}
