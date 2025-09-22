/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractJavaHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  private HttpClient client;

  @BeforeAll
  void setUp() {
    HttpClient.Builder httpClientBuilder =
        HttpClient.newBuilder()
            .connectTimeout(CONNECTION_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL);
    configureHttpClientBuilder(httpClientBuilder);
    HttpClient httpClient = httpClientBuilder.build();
    client = configureHttpClient(httpClient);
  }

  protected abstract void configureHttpClientBuilder(HttpClient.Builder httpClientBuilder);

  protected abstract HttpClient configureHttpClient(HttpClient httpClient);

  @Override
  public HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder().uri(uri).method(method, HttpRequest.BodyPublishers.noBody());
    headers.forEach(requestBuilder::header);
    if (client.version() == HttpClient.Version.HTTP_2) {
      // notify HttpClientTestServer that the request comes from java http client using http/2 so
      // that the server can work around http/2 tests failing with java.io.IOException: RST_STREAM
      // received
      requestBuilder.header("java-http-client-http2", "true");
    }
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
    // TODO nested client span is not created, but context is still injected
    //  which is not what the test expects
    optionsBuilder.disableTestWithClientParent();
    optionsBuilder.spanEndsAfterBody();

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          // unopened port or non routable address; or timeout
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())
              || uri.toString().contains("/read-timeout")) {
            attributes.remove(NETWORK_PROTOCOL_VERSION);
          }
          return attributes;
        });
  }

  @SuppressWarnings("Interruption") // test calls CompletableFuture.cancel with true
  @Test
  void cancelRequest() throws InterruptedException {
    String method = "GET";
    URI uri = resolveAddress("/long-request");

    CompletableFuture<String> future =
        testing.runWithSpan(
            "parent",
            () -> {
              HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(uri)
                      .method(method, HttpRequest.BodyPublishers.noBody())
                      .build();
              return client
                  .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenApply(HttpResponse::body)
                  .whenComplete(
                      (response, throwable) ->
                          testing.runWithSpan(
                              "child",
                              () -> {
                                if (throwable != null && throwable.getCause() != null) {
                                  Span.current()
                                      .setAttribute(
                                          "throwable", throwable.getCause().getClass().getName());
                                }
                              }))
                  // this stage is only added to trigger the whenComplete stage when this stage gets
                  // cancelled
                  .exceptionally(ex -> "cancelled");
            });

    // sleep a bit to let the request start
    Thread.sleep(100);
    future.cancel(true);
    assertThatThrownBy(future::get).isInstanceOf(CancellationException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_FULL, uri.toString()),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort()),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, method),
                            equalTo(
                                ErrorAttributes.ERROR_TYPE, CancellationException.class.getName())),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasStatus(StatusData.error()),
                span ->
                    span.hasName("child")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("throwable"), CancellationException.class.getName()))));
  }
}
