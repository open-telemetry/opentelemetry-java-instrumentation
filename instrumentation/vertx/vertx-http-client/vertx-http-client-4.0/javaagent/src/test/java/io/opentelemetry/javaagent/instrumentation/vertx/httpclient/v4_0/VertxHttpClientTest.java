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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxHttpClientTest extends AbstractHttpClientTest<Future<HttpClientRequest>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private final Vertx vertx = Vertx.vertx(new VertxOptions());
  private final HttpClient httpClient = buildClient(vertx);
  private HttpClient http2Client;
  private HttpServer http2Server;

  private static HttpClient buildClient(Vertx vertx) {
    HttpClientOptions clientOptions =
        new HttpClientOptions().setConnectTimeout(Math.toIntExact(CONNECTION_TIMEOUT.toMillis()));
    return vertx.createHttpClient(clientOptions);
  }

  @AfterAll
  void closeVertx() {
    vertx.close();
  }

  @AfterEach
  void closeHttp2Server() throws Exception {
    if (http2Server != null) {
      http2Server.close().toCompletionStage().toCompletableFuture().get(30, SECONDS);
      http2Server = null;
    }
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

  @Test
  void responseCallbacksRetainCallerContextAndParentFollowupRequest() throws Exception {
    CompletableFuture<HttpServerResponse> pendingResponse = new CompletableFuture<>();
    startHttp2Server(
        request -> {
          if (request.path().equals("/first")) {
            HttpServerResponse response = request.response().setChunked(true);
            response.write("body");
            pendingResponse.complete(response);
          } else {
            request.response().end("followup");
          }
        });

    HttpClientRequest request = newHttp2Request("/first");
    HttpClientRequest followupRequest = newHttp2Request("/followup");
    CompletableFuture<HttpClientResponse> responseFuture = new CompletableFuture<>();

    SpanContext callerContext =
        testing.runWithSpan(
            "parent",
            () -> {
              request
                  .response()
                  .onComplete(
                      asyncResult -> {
                        if (asyncResult.succeeded()) {
                          responseFuture.complete(asyncResult.result());
                        } else {
                          responseFuture.completeExceptionally(asyncResult.cause());
                        }
                      });
              request.end();
              return Span.current().getSpanContext();
            });

    HttpClientResponse response = responseFuture.get(30, SECONDS);
    CompletableFuture<SpanContext> bodyContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> endContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> frameContext = new CompletableFuture<>();
    CompletableFuture<Void> followupCompleted = new CompletableFuture<>();

    assertThat(Span.current().getSpanContext()).isNotEqualTo(callerContext);
    response.handler(
        body -> {
          if (bodyContext.complete(Span.current().getSpanContext())) {
            followupRequest
                .response()
                .onComplete(
                    asyncResult -> {
                      if (asyncResult.succeeded()) {
                        asyncResult
                            .result()
                            .end()
                            .onComplete(
                                endResult -> {
                                  if (endResult.succeeded()) {
                                    followupCompleted.complete(null);
                                  } else {
                                    followupCompleted.completeExceptionally(endResult.cause());
                                  }
                                });
                      } else {
                        followupCompleted.completeExceptionally(asyncResult.cause());
                      }
                    });
            followupRequest.end();
          }
        });
    response.endHandler(ignored -> endContext.complete(Span.current().getSpanContext()));
    response.customFrameHandler(ignored -> frameContext.complete(Span.current().getSpanContext()));

    HttpServerResponse serverResponse = pendingResponse.get(30, SECONDS);
    vertx.runOnContext(
        ignored -> {
          serverResponse.writeCustomFrame(0xf, 0, Buffer.buffer());
          serverResponse.end("end");
        });

    assertThat(bodyContext.get(30, SECONDS)).isEqualTo(callerContext);
    assertThat(endContext.get(30, SECONDS)).isEqualTo(callerContext);
    assertThat(frameContext.get(30, SECONDS)).isEqualTo(callerContext);
    followupCompleted.get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void concurrentHttp2ResponseCallbacksRetainTheirParentContexts() throws Exception {
    ConcurrentMap<String, HttpServerResponse> pendingResponses = new ConcurrentHashMap<>();
    startHttp2Server(
        request -> {
          HttpServerResponse response = request.response().setChunked(true);
          pendingResponses.put(request.path(), response);
          if (pendingResponses.size() == 2) {
            pendingResponses.values().forEach(responseToWrite -> responseToWrite.write("body"));
          }
        });

    HttpClientRequest firstRequest = newHttp2Request("/first");
    HttpClientRequest secondRequest = newHttp2Request("/second");
    CompletableFuture<HttpClientResponse> firstResponseFuture = new CompletableFuture<>();
    CompletableFuture<HttpClientResponse> secondResponseFuture = new CompletableFuture<>();

    SpanContext firstParent =
        testing.runWithSpan(
            "parent-one",
            () -> {
              firstRequest
                  .response()
                  .onComplete(
                      asyncResult -> {
                        if (asyncResult.succeeded()) {
                          firstResponseFuture.complete(asyncResult.result());
                        } else {
                          firstResponseFuture.completeExceptionally(asyncResult.cause());
                        }
                      });
              firstRequest.end();
              return Span.current().getSpanContext();
            });
    SpanContext secondParent =
        testing.runWithSpan(
            "parent-two",
            () -> {
              secondRequest
                  .response()
                  .onComplete(
                      asyncResult -> {
                        if (asyncResult.succeeded()) {
                          secondResponseFuture.complete(asyncResult.result());
                        } else {
                          secondResponseFuture.completeExceptionally(asyncResult.cause());
                        }
                      });
              secondRequest.end();
              return Span.current().getSpanContext();
            });

    HttpClientResponse firstResponse = firstResponseFuture.get(30, SECONDS);
    HttpClientResponse secondResponse = secondResponseFuture.get(30, SECONDS);
    CompletableFuture<SpanContext> firstContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> secondContext = new CompletableFuture<>();

    assertThat(Span.current().getSpanContext()).isNotEqualTo(firstParent);
    firstResponse.handler(ignored -> firstContext.complete(Span.current().getSpanContext()));
    secondResponse.handler(ignored -> secondContext.complete(Span.current().getSpanContext()));
    vertx.runOnContext(
        ignored -> pendingResponses.values().forEach(response -> response.end("done")));

    assertThat(firstParent.getTraceId()).isNotEqualTo(secondParent.getTraceId());
    assertThat(firstContext.get(30, SECONDS)).isEqualTo(firstParent);
    assertThat(secondContext.get(30, SECONDS)).isEqualTo(secondParent);

    testing.waitAndAssertSortedTraces(
        Comparator.comparing(trace -> trace.get(0).getName()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-one").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-two").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void responseExceptionHandlerRetainsCallerContext() throws Exception {
    CompletableFuture<HttpServerResponse> pendingResponse = new CompletableFuture<>();
    startHttp2Server(
        request -> {
          HttpServerResponse response = request.response().setChunked(true);
          response.write("partial");
          pendingResponse.complete(response);
        });

    HttpClientRequest request = newHttp2Request("/failure");
    CompletableFuture<HttpClientResponse> responseFuture = new CompletableFuture<>();
    SpanContext callerContext =
        testing.runWithSpan(
            "parent",
            () -> {
              request
                  .response()
                  .onComplete(
                      asyncResult -> {
                        if (asyncResult.succeeded()) {
                          responseFuture.complete(asyncResult.result());
                        } else {
                          responseFuture.completeExceptionally(asyncResult.cause());
                        }
                      });
              request.end();
              return Span.current().getSpanContext();
            });

    HttpClientResponse response = responseFuture.get(30, SECONDS);
    CompletableFuture<SpanContext> failureContext = new CompletableFuture<>();
    assertThat(Span.current().getSpanContext()).isNotEqualTo(callerContext);
    response.exceptionHandler(ignored -> failureContext.complete(Span.current().getSpanContext()));
    HttpServerResponse serverResponse = pendingResponse.get(30, SECONDS);
    vertx.runOnContext(ignored -> serverResponse.reset(8));

    assertThat(failureContext.get(30, SECONDS)).isEqualTo(callerContext);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  private void startHttp2Server(Handler<HttpServerRequest> requestHandler) throws Exception {
    http2Server =
        vertx
            .createHttpServer(new HttpServerOptions().setUseAlpn(false))
            .requestHandler(requestHandler)
            .listen(0, "localhost")
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, SECONDS);
  }

  private HttpClientRequest newHttp2Request(String path) throws Exception {
    if (http2Client == null) {
      http2Client =
          vertx.createHttpClient(
              new HttpClientOptions()
                  .setProtocolVersion(HttpVersion.HTTP_2)
                  .setHttp2ClearTextUpgrade(false)
                  .setUseAlpn(false)
                  .setMaxPoolSize(1));
    }
    return http2Client
        .request(HttpMethod.GET, http2Server.actualPort(), "localhost", path)
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
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
