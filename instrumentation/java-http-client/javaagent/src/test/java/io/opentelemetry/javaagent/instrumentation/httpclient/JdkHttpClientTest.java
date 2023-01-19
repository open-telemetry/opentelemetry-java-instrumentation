/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.LegacyHttpClientTestOptions;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JdkHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(CONNECTION_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

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
      throws Exception {
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
              } else {
                httpClientResult.complete(throwable.getCause());
              }
            });
  }

  @Override
  protected void configure(LegacyHttpClientTestOptions options) {
    options.disableTestCircularRedirects();
    options.enableTestReadTimeout();
    // TODO nested client span is not created, but context is still injected
    //  which is not what the test expects
    options.disableTestWithClientParent();
  }
}
