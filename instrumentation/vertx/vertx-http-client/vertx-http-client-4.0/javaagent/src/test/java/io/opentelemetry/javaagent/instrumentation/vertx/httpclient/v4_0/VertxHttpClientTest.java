/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v4_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxHttpClientTest extends AbstractHttpClientTest<Future<HttpClientRequest>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final Vertx vertx = Vertx.vertx(new VertxOptions());
  private final HttpClient httpClient = buildClient(vertx);

  private static HttpClient buildClient(Vertx vertx) {
    HttpClientOptions clientOptions =
        new HttpClientOptions().setConnectTimeout(Math.toIntExact(CONNECTION_TIMEOUT.toMillis()));
    return vertx.createHttpClient(clientOptions);
  }

  @AfterAll
  void closeVertx() {
    vertx.close();
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
    return sendRequest(request).get(30, SECONDS);
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

  // Regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/19289
  // Drives a single request through sendHead() followed by end(), which are two separate
  // write-triggering calls. The instrumentation must start a span and inject the trace context
  // exactly once, on the first call. Before the fix it re-started a span and re-injected on end(),
  // which left the exported client span different from the context propagated to the server, so
  // the server span was parented to a client span that was never exported. Asserting the full
  // parent -> client -> server chain therefore fails on the old behavior and passes with the fix.
  //
  // sendHead() is deprecated for removal in recent Vert.x versions, but it is called deliberately
  // here: it is one of the two write-triggering methods the instrumentation advice hooks alongside
  // end(), so it is exactly the code path this regression test needs to exercise.
  @SuppressWarnings({"deprecation", "removal"})
  @Test
  void injectsContextOnceWhenSendHeadPrecedesEnd() throws Exception {
    URI uri = resolveAddress("/success");
    RequestOptions requestOptions =
        new RequestOptions().setMethod(HttpMethod.GET).setAbsoluteURI(uri.toString());

    int responseCode =
        testing.runWithSpan(
            "parent",
            () -> {
              HttpClientRequest request =
                  httpClient
                      .request(requestOptions)
                      .toCompletionStage()
                      .toCompletableFuture()
                      .get(30, SECONDS);

              CompletableFuture<Integer> result = new CompletableFuture<>();
              request
                  .response()
                  .onComplete(
                      asyncResult -> {
                        if (asyncResult.succeeded()) {
                          result.complete(asyncResult.result().statusCode());
                        } else {
                          result.completeExceptionally(asyncResult.cause());
                        }
                      });

              // Flush the request head before ending the request, so the headers (and the injected
              // trace context) are written on sendHead() and end() is a genuine second call.
              request.sendHead().toCompletionStage().toCompletableFuture().get(30, SECONDS);
              request.end();

              return result.get(30, SECONDS);
            });

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SERVER).hasParent(trace.getSpan(1))));
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

    // Disable remote connection tests on Windows due to vertx creating extra spans
    if (OS.WINDOWS.isCurrentOs()) {
      optionsBuilder.setTestRemoteConnection(false);
    }
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
