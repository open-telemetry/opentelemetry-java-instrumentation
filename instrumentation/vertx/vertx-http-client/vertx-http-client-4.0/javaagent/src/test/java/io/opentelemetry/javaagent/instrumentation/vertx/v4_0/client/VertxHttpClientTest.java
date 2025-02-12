/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxHttpClientTest extends AbstractHttpClientTest<Future<HttpClientRequest>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final HttpClient httpClient = buildClient();

  private static HttpClient buildClient() {
    Vertx vertx = Vertx.vertx(new VertxOptions());
    HttpClientOptions clientOptions =
        new HttpClientOptions().setConnectTimeout(Math.toIntExact(CONNECTION_TIMEOUT.toMillis()));
    return vertx.createHttpClient(clientOptions);
  }

  @Override
  public Future<HttpClientRequest> buildRequest(
      String method, URI uri, Map<String, String> headers) {
    RequestOptions requestOptions =
        new RequestOptions().setMethod(HttpMethod.valueOf(method)).setAbsoluteURI(uri.toString());
    headers.forEach(requestOptions::putHeader);
    return httpClient.request(requestOptions);
  }

  private static CompletableFuture<Integer> sendRequest(Future<HttpClientRequest> request) {
    CompletableFuture<Integer> future = new CompletableFuture<>();

    request
        .compose(
            req ->
                req.send()
                    .onComplete(
                        asyncResult -> {
                          if (asyncResult.succeeded()) {
                            future.complete(asyncResult.result().statusCode());
                          } else {
                            future.completeExceptionally(asyncResult.cause());
                          }
                        }))
        .onFailure(future::completeExceptionally);

    return future;
  }

  @Override
  public int sendRequest(
      Future<HttpClientRequest> request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    // Vertx doesn't seem to provide any synchronous API so bridge through a callback
    return sendRequest(request).get(30, TimeUnit.SECONDS);
  }

  @Override
  public void sendRequestWithCallback(
      Future<HttpClientRequest> request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    sendRequest(request)
        .whenComplete((status, throwable) -> httpClientResult.complete(() -> status, throwable));
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();
    optionsBuilder.disableTestReusedRequest();
    optionsBuilder.disableTestHttps();
    optionsBuilder.disableTestReadTimeout();
    optionsBuilder.setHttpAttributes(VertxHttpClientTest::getHttpAttributes);
    optionsBuilder.setExpectedClientSpanNameMapper(VertxHttpClientTest::getExpectedClientSpanName);

    optionsBuilder.setSingleConnectionFactory(VertxSingleConnection::new);
  }

  private static Set<AttributeKey<?>> getHttpAttributes(URI uri) {
    String uriString = uri.toString();
    // http://localhost:61/ => unopened port, http://192.0.2.1/ => non routable address
    if ("http://localhost:61/".equals(uriString) || "http://192.0.2.1/".equals(uriString)) {
      return emptySet();
    }
    return HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES;
  }

  private static String getExpectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://192.0.2.1/": // non routable address
        return "CONNECT";
      default:
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
    }
  }
}
