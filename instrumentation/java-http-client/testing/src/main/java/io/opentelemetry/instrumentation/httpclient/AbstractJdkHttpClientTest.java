/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractJdkHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  private HttpClient client;

  @BeforeAll
  void setUp() {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECTION_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    client = configureHttpClient(httpClient);
  }

  protected abstract HttpClient configureHttpClient(HttpClient httpClient);

  @Override
  public HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder().uri(uri).method(method, HttpRequest.BodyPublishers.noBody());
    headers.forEach(requestBuilder::header);
    if (uri.toString().contains("/read-timeout")) {
      requestBuilder.timeout(READ_TIMEOUT);
    }
    return requestBuilder.build();
  }

  @Override
  public int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers)
      throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  @Override
  public void sendRequestWithCallback(
      HttpRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    client
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .whenComplete(
            (response, throwable) -> {
              if (throwable == null) {
                httpClientResult.complete(response.statusCode());
              } else if (throwable.getCause() != null) {
                httpClientResult.complete(throwable.getCause());
              } else {
                httpClientResult.complete(
                    new IllegalStateException("throwable.getCause() returned null", throwable));
              }
            });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestCircularRedirects();
    optionsBuilder.enableTestReadTimeout();
    // TODO nested client span is not created, but context is still injected
    //  which is not what the test expects
    optionsBuilder.disableTestWithClientParent();
  }
}
