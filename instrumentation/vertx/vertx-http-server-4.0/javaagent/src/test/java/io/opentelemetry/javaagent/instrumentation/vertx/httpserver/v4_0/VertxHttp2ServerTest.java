/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.api.trace.StatusCode.UNSET;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VertxHttp2ServerTest {
  private static final String SCOPE = "io.opentelemetry.vertx-http-server-4.0";
  private static final String TRACE_ID = "01010101010101010101010101010101";
  private static final String PARENT_ID = "0000000000000456";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final Vertx vertx = Vertx.vertx();
  private int port;

  @AfterEach
  void close() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get(10, SECONDS);
  }

  private HttpClient start(HttpClient.Version version, Handler<HttpServerRequest> handler)
      throws Exception {
    port = PortUtils.findOpenPort();
    HttpServer server =
        vertx.createHttpServer(
            new HttpServerOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setKeyCertOptions(
                    new PfxOptions()
                        .setPath(System.getProperty("test.keystore"))
                        .setPassword("testing")));
    server
        .requestHandler(handler)
        .listen(port, "localhost")
        .toCompletionStage()
        .toCompletableFuture()
        .get(10, SECONDS);

    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(Paths.get(System.getProperty("test.keystore")))) {
      store.load(input, "testing".toCharArray());
    }
    TrustManagerFactory trust =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(store);
    SSLContext ssl = SSLContext.getInstance("TLS");
    ssl.init(null, trust.getTrustManagers(), null);
    return HttpClient.newBuilder().version(version).sslContext(ssl).build();
  }

  private CompletableFuture<HttpResponse<String>> send(
      HttpClient client, String path, String traceId) {
    return client.sendAsync(
        HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("traceparent", "00-" + traceId + "-" + PARENT_ID + "-01")
            .header("user-agent", "test-client")
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @Tag("http1")
  void tlsHttp1ServerMetrics() throws Exception {
    tlsServerMetrics(HttpClient.Version.HTTP_1_1);
  }

  @Test
  void tlsHttp2ServerMetrics() throws Exception {
    tlsServerMetrics(HttpClient.Version.HTTP_2);
  }

  private void tlsServerMetrics(HttpClient.Version version) throws Exception {
    Router router = Router.router(vertx);
    router
        .get("/hello/:id")
        .handler(
            ctx -> {
              assertThat(ctx.request().version())
                  .isEqualTo(
                      version == HttpClient.Version.HTTP_2
                          ? HttpVersion.HTTP_2
                          : HttpVersion.HTTP_1_1);
              assertThat(ctx.request().isSSL()).isTrue();
              vertx.setTimer(
                  1, ignored -> testing.runWithSpan("handler", () -> ctx.response().end("hello")));
            });
    HttpClient client = start(version, router);
    HttpResponse<String> response = send(client, "/hello/123", TRACE_ID).get(10, SECONDS);
    assertThat(response.version()).isEqualTo(version);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasName("GET /hello/:id")
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(HTTP_ROUTE, "/hello/:id"),
                            equalTo(URL_SCHEME, "https"),
                            equalTo(URL_PATH, "/hello/123"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(USER_AGENT_ORIGINAL, "test-client"),
                            satisfies(CLIENT_ADDRESS, val -> val.isNotBlank()),
                            satisfies(NETWORK_PEER_ADDRESS, val -> val.isNotBlank()),
                            satisfies(NETWORK_PEER_PORT, val -> val.isPositive()),
                            equalTo(
                                NETWORK_PROTOCOL_VERSION,
                                version == HttpClient.Version.HTTP_2 ? "2" : "1.1")),
                span -> span.hasName("handler").hasParent(trace.getSpan(0))));
    assertDuration(version == HttpClient.Version.HTTP_2 ? SCOPE : "io.opentelemetry.netty-4.1", 1);
  }

  @Test
  void concurrentStreamsHaveIndependentContexts() throws Exception {
    CompletableFuture<HttpServerRequest> slowRequest = new CompletableFuture<>();
    CompletableFuture<HttpServerRequest> fastRequest = new CompletableFuture<>();
    String secondTraceId = "03030303030303030303030303030303";
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request -> {
              assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
              if (request.path().equals("/slow")) {
                request.bodyHandler(
                    body ->
                        testing.runWithSpan(
                            "slow body",
                            () -> {
                              assertThat(body.toString()).isEqualTo("request body");
                              slowRequest.complete(request);
                            }));
              } else {
                fastRequest.complete(request);
                testing.runWithSpan("fast", () -> request.response().end("fast"));
              }
            });
    CompletableFuture<HttpResponse<String>> slow =
        client.sendAsync(
            HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/slow"))
                .timeout(Duration.ofSeconds(10))
                .header("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01")
                .POST(HttpRequest.BodyPublishers.ofString("request body"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    HttpServerRequest slowServerRequest = slowRequest.get(10, SECONDS);
    HttpResponse<String> fast = send(client, "/fast", secondTraceId).get(10, SECONDS);
    assertThat(fast.version()).isEqualTo(HttpClient.Version.HTTP_2);
    assertThat(fast.body()).isEqualTo("fast");
    assertThat(slow.isDone()).isFalse();
    assertThat(fastRequest.get(10, SECONDS).connection()).isSameAs(slowServerRequest.connection());
    assertDuration(SCOPE, 1);

    vertx.runOnContext(ignored -> slowServerRequest.response().end("slow"));
    assertThat(slow.get(10, SECONDS).body()).isEqualTo("slow");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                span -> span.hasName("slow body").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(secondTraceId).hasParentSpanId(PARENT_ID),
                span -> span.hasName("fast").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 2);
  }

  @Test
  void backpressureDrainHandlersRetainStreamContexts() throws Exception {
    String secondTraceId = "03030303030303030303030303030303";
    CompletableFuture<Void> drained = new CompletableFuture<>();
    CompletableFuture<Void> responsesEnded = new CompletableFuture<>();
    AtomicInteger drainCount = new AtomicInteger();
    AtomicInteger responseCount = new AtomicInteger();
    AtomicInteger firstBytes = new AtomicInteger();
    AtomicInteger secondBytes = new AtomicInteger();
    CompletableFuture<HttpServerRequest> firstServerRequest = new CompletableFuture<>();
    CompletableFuture<HttpServerRequest> secondServerRequest = new CompletableFuture<>();
    start(
        HttpClient.Version.HTTP_2,
        request -> {
          assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
          if (request.path().equals("/drain/first")) {
            firstServerRequest.complete(request);
          } else {
            secondServerRequest.complete(request);
          }
          request
              .response()
              .setWriteQueueMaxSize(1024)
              .drainHandler(
                  ignored ->
                      testing.runWithSpan(
                          "drain " + request.path(),
                          () -> {
                            request.response().end();
                            if (drainCount.incrementAndGet() == 2) {
                              drained.complete(null);
                            }
                          }));
          byte[] chunk = new byte[8192];
          for (int i = 0; i < 512; i++) {
            request.response().write(Buffer.buffer(chunk));
          }
          assertThat(request.response().writeQueueFull()).isTrue();
        });
    io.vertx.core.http.HttpClient client =
        vertx.createHttpClient(
            new HttpClientOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setMaxPoolSize(1)
                .setTrustOptions(
                    new PfxOptions()
                        .setPath(System.getProperty("test.keystore"))
                        .setPassword("testing")));
    try {
      HttpClientResponse first =
          sendAndPause(client, "/drain/first", TRACE_ID, firstBytes, responseCount, responsesEnded)
              .get(10, SECONDS);
      HttpClientResponse second =
          sendAndPause(
                  client,
                  "/drain/second",
                  secondTraceId,
                  secondBytes,
                  responseCount,
                  responsesEnded)
              .get(10, SECONDS);
      assertThat(firstServerRequest.get(10, SECONDS).connection())
          .isSameAs(secondServerRequest.get(10, SECONDS).connection());
      assertThat(drained.isDone()).isFalse();

      CompletableFuture<Void> resumed = new CompletableFuture<>();
      vertx.runOnContext(
          ignored -> {
            first.resume();
            second.resume();
            resumed.complete(null);
          });
      resumed.get(10, SECONDS);
      drained.get(10, SECONDS);
      responsesEnded.get(10, SECONDS);
      assertThat(first.statusCode()).isEqualTo(200);
      assertThat(second.statusCode()).isEqualTo(200);
      assertThat(firstBytes.get()).isGreaterThan(65_536);
      assertThat(secondBytes.get()).isGreaterThan(65_536);
    } finally {
      client.close();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                span -> span.hasName("drain /drain/first").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(secondTraceId).hasParentSpanId(PARENT_ID),
                span -> span.hasName("drain /drain/second").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void responseEndsBeforeUpload(boolean trailers) throws Exception {
    CompletableFuture<Void> requestEnd = new CompletableFuture<>();
    CompletableFuture<Void> responseEnd = new CompletableFuture<>();
    CompletableFuture<Void> responseClose = new CompletableFuture<>();
    start(
        HttpClient.Version.HTTP_2,
        request -> {
          assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
          request.endHandler(
              ignored -> {
                testing.runWithSpan("late request end", () -> {});
                requestEnd.complete(null);
              });
          request
              .response()
              .closeHandler(
                  ignored -> {
                    testing.runWithSpan("response close", () -> {});
                    responseClose.complete(null);
                  });
          if (trailers) {
            request.response().putTrailer("x-finished", "yes");
          }
          request.response().endHandler(ignored -> responseEnd.complete(null)).end();
        });
    io.vertx.core.http.HttpClient client =
        vertx.createHttpClient(
            new HttpClientOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setTrustOptions(
                    new PfxOptions()
                        .setPath(System.getProperty("test.keystore"))
                        .setPassword("testing")));
    try {
      HttpClientRequest request =
          client
              .request(
                  new RequestOptions()
                      .setHost("localhost")
                      .setPort(port)
                      .setURI("/early")
                      .setMethod(HttpMethod.POST))
              .toCompletionStage()
              .toCompletableFuture()
              .get(10, SECONDS);
      request.putHeader("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01").setChunked(true);
      CompletableFuture<Buffer> responseBody =
          request
              .response()
              .compose(
                  response -> {
                    assertThat(response.version()).isEqualTo(HttpVersion.HTTP_2);
                    assertThat(response.statusCode()).isEqualTo(200);
                    return response
                        .body()
                        .map(
                            body -> {
                              assertThat(response.getTrailer("x-finished"))
                                  .isEqualTo(trailers ? "yes" : null);
                              return body;
                            });
                  })
              .toCompletionStage()
              .toCompletableFuture();
      request.write("unfinished upload").toCompletionStage().toCompletableFuture().get(10, SECONDS);
      assertThat(responseBody.get(10, SECONDS).length()).isZero();
      responseEnd.get(10, SECONDS);
      assertThat(requestEnd.isDone()).isFalse();
      assertDuration(SCOPE, 1);
      request.end().toCompletionStage().toCompletableFuture().get(10, SECONDS);
      requestEnd.get(10, SECONDS);
      responseClose.get(10, SECONDS);
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                  span -> span.hasName("late request end").hasParent(trace.getSpan(0)),
                  span -> span.hasName("response close").hasParent(trace.getSpan(0))));
      assertDuration(SCOPE, 1);
    } finally {
      client.close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void delayedResponseEndsAfterRequest(boolean trailers) throws Exception {
    CompletableFuture<Void> requestEnd = new CompletableFuture<>();
    CompletableFuture<Void> responseEnd = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request ->
                request.endHandler(
                    ignored -> {
                      requestEnd.complete(null);
                      vertx.setTimer(
                          1,
                          timer -> {
                            if (trailers) {
                              request.response().putTrailer("x-finished", "yes");
                            }
                            request.response().endHandler(end -> responseEnd.complete(null)).end();
                          });
                    }));

    HttpResponse<String> response = send(client, "/delayed", TRACE_ID).get(10, SECONDS);
    requestEnd.get(10, SECONDS);
    responseEnd.get(10, SECONDS);
    assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasAttributesSatisfying(equalTo(HTTP_RESPONSE_STATUS_CODE, 200))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void responseLifecycleHandlersRetainContextWhenEndedFromNeutralContext() throws Exception {
    CompletableFuture<Void> responseEnd = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request -> {
              request
                  .response()
                  .headersEndHandler(ignored -> testing.runWithSpan("headers end", () -> {}))
                  .bodyEndHandler(ignored -> testing.runWithSpan("body end", () -> {}))
                  .endHandler(
                      ignored -> {
                        testing.runWithSpan("response end", () -> {});
                        responseEnd.complete(null);
                      });
              vertx.setTimer(
                  1,
                  ignored -> {
                    try (Scope neutral = Context.root().makeCurrent()) {
                      request.response().end("response body");
                    }
                  });
            });

    HttpResponse<String> response = send(client, "/neutral-context", TRACE_ID).get(10, SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("response body");
    responseEnd.get(10, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                span -> span.hasName("headers end").hasParent(trace.getSpan(0)),
                span -> span.hasName("body end").hasParent(trace.getSpan(0)),
                span -> span.hasName("response end").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 1);
  }

  @Test
  @EnabledIfSystemProperty(named = "test.vertx.http2.headerOnlyCallback", matches = "true")
  void headerOnlyEndCallbackRetainsContextWhenEndedFromNeutralContext() throws Exception {
    CompletableFuture<Void> responseEnd = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request ->
                vertx.setTimer(
                    1,
                    ignored -> {
                      try (Scope neutral = Context.root().makeCurrent()) {
                        request
                            .response()
                            .end(
                                result -> {
                                  assertThat(result.succeeded()).isTrue();
                                  testing.runWithSpan("response end callback", () -> {});
                                  responseEnd.complete(null);
                                });
                      }
                    }));

    HttpResponse<String> response = send(client, "/header-only", TRACE_ID).get(10, SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEmpty();
    responseEnd.get(10, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                span -> span.hasName("response end callback").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void sendFileHeaderHandlerRetainsContextWhenCompletedFromNeutralContext() throws Exception {
    Path file = Files.createTempFile("vertx-http2-send-file-context", ".txt");
    try {
      Files.writeString(file, "send file");
      CompletableFuture<Void> headersEnd = new CompletableFuture<>();
      CompletableFuture<Void> sendFileEnd = new CompletableFuture<>();
      HttpClient client =
          start(
              HttpClient.Version.HTTP_2,
              request -> {
                request
                    .response()
                    .headersEndHandler(
                        ignored -> {
                          testing.runWithSpan("sendFile headers end", () -> {});
                          headersEnd.complete(null);
                        });
                vertx.setTimer(
                    1,
                    ignored -> {
                      try (Scope neutral = Context.root().makeCurrent()) {
                        request
                            .response()
                            .sendFile(
                                file.toString(),
                                result -> {
                                  assertThat(result.succeeded()).isTrue();
                                  testing.runWithSpan("sendFile callback", () -> {});
                                  sendFileEnd.complete(null);
                                });
                      }
                    });
              });

      HttpResponse<String> response = send(client, "/send-file-context", TRACE_ID).get(10, SECONDS);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("send file");
      headersEnd.get(10, SECONDS);
      sendFileEnd.get(10, SECONDS);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                  span -> span.hasName("sendFile headers end").hasParent(trace.getSpan(0)),
                  span -> span.hasName("sendFile callback").hasParent(trace.getSpan(0))));
      assertDuration(SCOPE, 1);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void callbacksRetainRegistrationChildAndBaggage() throws Exception {
    Path file = Files.createTempFile("vertx-http2-send-file-child-context", ".txt");
    try {
      Files.writeString(file, "send file");
      CompletableFuture<Void> lifecycleCallbacks = new CompletableFuture<>();
      CompletableFuture<Void> writeCallback = new CompletableFuture<>();
      CompletableFuture<Void> sendFileCallbacks = new CompletableFuture<>();
      AtomicInteger lifecycleCallbackCount = new AtomicInteger();
      HttpClient client =
          start(
              HttpClient.Version.HTTP_2,
              request -> {
                Baggage baggage = Baggage.builder().put("tenant", "acme").build();
                try (Scope ignored = baggage.makeCurrent()) {
                  testing.runWithSpan(
                      "registration child",
                      () -> {
                        if (request.path().equals("/lifecycle-child-context")) {
                          request
                              .response()
                              .headersEndHandler(
                                  ignored2 ->
                                      assertCallbackContext(
                                          "headers callback",
                                          lifecycleCallbackCount,
                                          lifecycleCallbacks))
                              .bodyEndHandler(
                                  ignored2 ->
                                      assertCallbackContext(
                                          "body callback",
                                          lifecycleCallbackCount,
                                          lifecycleCallbacks))
                              .endHandler(
                                  ignored2 ->
                                      assertCallbackContext(
                                          "end callback",
                                          lifecycleCallbackCount,
                                          lifecycleCallbacks));
                          vertx.setTimer(
                              1,
                              ignored2 -> {
                                try (Scope neutral = Context.root().makeCurrent()) {
                                  request.response().end("body");
                                }
                              });
                        } else if (request.path().equals("/write-child-context")) {
                          request
                              .response()
                              .write(
                                  Buffer.buffer("chunk"),
                                  result -> {
                                    assertThat(result.succeeded()).isTrue();
                                    assertCallbackContext("write callback");
                                    writeCallback.complete(null);
                                  });
                          vertx.setTimer(
                              1,
                              ignored2 -> {
                                try (Scope neutral = Context.root().makeCurrent()) {
                                  request.response().end("done");
                                }
                              });
                        } else {
                          request
                              .response()
                              .headersEndHandler(
                                  ignored2 -> assertCallbackContext("sendFile headers callback"));
                          request
                              .response()
                              .sendFile(
                                  file.toString(),
                                  result -> {
                                    assertThat(result.succeeded()).isTrue();
                                    assertCallbackContext("sendFile callback");
                                    sendFileCallbacks.complete(null);
                                  });
                        }
                      });
                }
              });

      HttpResponse<String> lifecycle =
          send(client, "/lifecycle-child-context", "04040404040404040404040404040404")
              .get(10, SECONDS);
      HttpResponse<String> write =
          send(client, "/write-child-context", "05050505050505050505050505050505").get(10, SECONDS);
      HttpResponse<String> sendFile =
          send(client, "/send-file-child-context", "06060606060606060606060606060606")
              .get(10, SECONDS);
      assertThat(lifecycle.statusCode()).isEqualTo(200);
      assertThat(write.statusCode()).isEqualTo(200);
      assertThat(sendFile.statusCode()).isEqualTo(200);
      lifecycleCallbacks.get(10, SECONDS);
      writeCallback.get(10, SECONDS);
      sendFileCallbacks.get(10, SECONDS);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SERVER)
                          .hasTraceId("04040404040404040404040404040404")
                          .hasParentSpanId(PARENT_ID),
                  span ->
                      span.hasName("registration child")
                          .hasParentSpanId(trace.getSpan(0).getSpanContext().getSpanId()),
                  span -> span.hasName("headers callback").hasParent(trace.getSpan(1)),
                  span -> span.hasName("body callback").hasParent(trace.getSpan(1)),
                  span -> span.hasName("end callback").hasParent(trace.getSpan(1))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SERVER)
                          .hasTraceId("05050505050505050505050505050505")
                          .hasParentSpanId(PARENT_ID),
                  span ->
                      span.hasName("registration child")
                          .hasParentSpanId(trace.getSpan(0).getSpanContext().getSpanId()),
                  span -> span.hasName("write callback").hasParent(trace.getSpan(1))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SERVER)
                          .hasTraceId("06060606060606060606060606060606")
                          .hasParentSpanId(PARENT_ID),
                  span ->
                      span.hasName("registration child")
                          .hasParentSpanId(trace.getSpan(0).getSpanContext().getSpanId()),
                  span -> span.hasName("sendFile headers callback").hasParent(trace.getSpan(1)),
                  span -> span.hasName("sendFile callback").hasParent(trace.getSpan(1))));
      assertDuration(SCOPE, 3);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static void assertCallbackContext(
      String spanName, AtomicInteger callbackCount, CompletableFuture<Void> callbacksComplete) {
    assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("acme");
    testing.runWithSpan(spanName, () -> {});
    if (callbackCount.incrementAndGet() == 3) {
      callbacksComplete.complete(null);
    }
  }

  private static void assertCallbackContext(String spanName) {
    assertThat(Baggage.current().getEntryValue("tenant")).isEqualTo("acme");
    testing.runWithSpan(spanName, () -> {});
  }

  @Test
  void trailersAddedDuringHeaderPreparationCompleteOnTerminalWrite() throws Exception {
    CompletableFuture<Void> writeCallback = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request ->
                request
                    .response()
                    .headersEndHandler(
                        ignored -> request.response().trailers().set("x-finished", "yes"))
                    .end(
                        "response body",
                        result -> {
                          assertThat(result.succeeded()).isTrue();
                          testing.runWithSpan("write callback", () -> {});
                          writeCallback.complete(null);
                        }));

    HttpResponse<String> response = send(client, "/trailers", TRACE_ID).get(10, SECONDS);
    writeCallback.get(10, SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("response body");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID),
                span -> span.hasName("write callback").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 1);
  }

  @Test
  @EnabledIfSystemProperty(named = "test.vertx.http2.syncTrailerWrite", matches = "true")
  void synchronousDataCallbackWithLateTrailersRetainsContext() throws Exception {
    CompletableFuture<Boolean> dataCallbackWasSynchronous = new CompletableFuture<>();
    CompletableFuture<Boolean> resetAccepted = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request -> {
              request
                  .response()
                  .headersEndHandler(
                      ignored -> request.response().trailers().set("x-finished", "yes"));
              vertx.setTimer(
                  1,
                  ignored -> {
                    AtomicBoolean endReturned = new AtomicBoolean();
                    request
                        .response()
                        .end(
                            "response body",
                            result -> {
                              testing.runWithSpan("data write callback", () -> {});
                              dataCallbackWasSynchronous.complete(!endReturned.get());
                              resetAccepted.complete(request.response().reset(8));
                            });
                    endReturned.set(true);
                  });
            });

    CompletableFuture<HttpResponse<String>> response =
        send(client, "/synchronous-trailers", TRACE_ID);
    boolean reset = resetAccepted.get(10, SECONDS);
    assertThat(reset).isTrue();
    assertThatThrownBy(() -> response.get(10, SECONDS)).isInstanceOf(ExecutionException.class);
    assertThat(dataCallbackWasSynchronous.get(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(ERROR)),
                span -> span.hasName("data write callback").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void customFrameAndPriorityCallbacksRetainStreamContext() throws Exception {
    CompletableFuture<HttpServerRequest> serverRequest = new CompletableFuture<>();
    AtomicInteger callbacks = new AtomicInteger();
    start(
        HttpClient.Version.HTTP_2,
        request -> {
          request.customFrameHandler(
              frame -> {
                testing.runWithSpan("custom frame callback", () -> {});
                if (callbacks.incrementAndGet() == 2) {
                  request.response().end("done");
                }
              });
          try {
            request
                .getClass()
                .getMethod("streamPriorityHandler", Handler.class)
                .invoke(
                    request,
                    (Handler<StreamPriority>)
                        priority -> {
                          testing.runWithSpan("priority change callback", () -> {});
                          if (callbacks.incrementAndGet() == 2) {
                            request.response().end("done");
                          }
                        });
          } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
          }
          serverRequest.complete(request);
        });
    io.vertx.core.http.HttpClient client =
        vertx.createHttpClient(
            new HttpClientOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setMaxPoolSize(1)
                .setTrustOptions(
                    new PfxOptions()
                        .setPath(System.getProperty("test.keystore"))
                        .setPassword("testing")));
    try {
      String traceId = "03030303030303030303030303030303";
      HttpClientRequest clientRequest = openStreamingRequest(client, "/callbacks", traceId);
      CompletableFuture<HttpClientResponse> response =
          clientRequest.response().toCompletionStage().toCompletableFuture();
      serverRequest.get(10, SECONDS);
      clientRequest.writeCustomFrame(0xB, 0, Buffer.buffer());
      clientRequest.setStreamPriority(new StreamPriority().setWeight((short) 10));
      clientRequest.end();
      assertThat(response.get(10, SECONDS).statusCode()).isEqualTo(200);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SERVER).hasTraceId(traceId).hasParentSpanId(PARENT_ID),
                  span -> span.hasName("custom frame callback").hasParent(trace.getSpan(0)),
                  span -> span.hasName("priority change callback").hasParent(trace.getSpan(0))));
    } finally {
      client.close();
    }

    assertDuration(SCOPE, 1);
  }

  @Test
  void formDecoderFailureCanRecoverAsynchronously() throws Exception {
    CompletableFuture<Throwable> decoderFailure = new CompletableFuture<>();
    CompletableFuture<Throwable> responseFailure = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request -> {
              assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
              request.setExpectMultipart(true);
              request
                  .response()
                  .exceptionHandler(
                      error -> {
                        if (responseFailure.complete(error)) {
                          testing.runWithSpan("response exception", () -> {});
                        }
                      });
              request.exceptionHandler(
                  error -> {
                    testing.runWithSpan("request exception", () -> {});
                    vertx.setTimer(
                        200,
                        ignored ->
                            testing.runWithSpan(
                                "recovery",
                                () -> request.response().setStatusCode(400).end("invalid form")));
                    decoderFailure.complete(error);
                  });
              request.handler(ignored -> {});
            });
    HttpResponse<String> response =
        client
            .sendAsync(
                HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/invalid-form"))
                    .timeout(Duration.ofSeconds(10))
                    .header("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("field=%GG&"))
                    .build(),
                HttpResponse.BodyHandlers.ofString())
            .get(10, SECONDS);
    Throwable error = decoderFailure.get(10, SECONDS);
    assertThat(error.getClass().getName())
        .isEqualTo(
            "io.netty.handler.codec.http.multipart.HttpPostRequestDecoder$ErrorDataDecoderException");
    assertThat(responseFailure.get(10, SECONDS)).isSameAs(error);
    assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).isEqualTo("invalid form");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(ERROR))
                        .hasException(error)
                        .hasAttributesSatisfying(
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 400),
                            equalTo(ERROR_TYPE, error.getClass().getName())),
                span -> span.hasName("request exception").hasParent(trace.getSpan(0)),
                span -> span.hasName("response exception").hasParent(trace.getSpan(0)),
                span -> span.hasName("recovery").hasParent(trace.getSpan(0))));
    testing.waitAndAssertMetrics(
        SCOPE,
        "http.server.request.duration",
        metrics ->
            metrics.satisfies(
                data -> {
                  assertThat(
                          data.stream()
                              .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                              .mapToLong(point -> point.getCount())
                              .sum())
                      .isEqualTo(1);
                  assertThat(
                          data.stream()
                              .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                              .mapToDouble(point -> point.getSum())
                              .sum())
                      .isGreaterThan(0.1);
                  data.forEach(
                      metric ->
                          assertThat(metric.getHistogramData().getPoints())
                              .allSatisfy(
                                  point ->
                                      assertThat(
                                              point.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                                          .isEqualTo(400L)));
                }));
  }

  @Test
  void sendFileFailureCanRecover() throws Exception {
    Path missingFile = Files.createTempFile("vertx-http2-missing", ".txt");
    Files.delete(missingFile);
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request ->
                request
                    .response()
                    .sendFile(
                        missingFile.toString(),
                        result -> {
                          assertThat(result.failed()).isTrue();
                          testing.runWithSpan("sendFile callback", () -> {});
                          vertx.setTimer(
                              200,
                              ignored -> request.response().setStatusCode(404).end("not found"));
                        }));

    HttpResponse<String> response = send(client, "/missing", TRACE_ID).get(10, SECONDS);
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).isEqualTo("not found");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasAttributesSatisfying(equalTo(HTTP_RESPONSE_STATUS_CODE, 404)),
                span -> span.hasName("sendFile callback").hasParent(trace.getSpan(0))));
    testing.waitAndAssertMetrics(
        SCOPE,
        "http.server.request.duration",
        metrics ->
            metrics.satisfies(
                data -> {
                  assertThat(
                          data.stream()
                              .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                              .mapToLong(point -> point.getCount())
                              .sum())
                      .isEqualTo(1);
                  assertThat(
                          data.stream()
                              .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                              .mapToDouble(point -> point.getSum())
                              .sum())
                      .isGreaterThan(0.1);
                }));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void failureEndsRequest(boolean reset) throws Exception {
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request -> {
              assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
              if (reset) {
                request.response().reset(8);
              } else {
                request.connection().close();
              }
            });
    assertThatThrownBy(() -> send(client, "/reset", TRACE_ID).get(10, SECONDS))
        .isInstanceOf(ExecutionException.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(ERROR))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void failedNonTerminalDataWriteEndsResponse() throws Exception {
    CompletableFuture<Throwable> writeFailure = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request -> {
              request
                  .response()
                  .headersEndHandler(ignored -> assertThat(request.response().reset(8)).isTrue());
              request
                  .response()
                  .write(
                      Buffer.buffer("pending data"),
                      result -> {
                        if (result.failed()) {
                          writeFailure.complete(result.cause());
                        } else {
                          writeFailure.completeExceptionally(new AssertionError("write succeeded"));
                        }
                      });
            });
    assertThatThrownBy(() -> send(client, "/failed-write", TRACE_ID).get(10, SECONDS))
        .isInstanceOf(ExecutionException.class);
    assertThat(writeFailure.get(10, SECONDS)).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(ERROR))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void resetAfterInboundEndRetainsCallbackContext() throws Exception {
    CompletableFuture<Void> inboundEnded = new CompletableFuture<>();
    CompletableFuture<Void> responseException = new CompletableFuture<>();
    AtomicInteger responseExceptionCount = new AtomicInteger();
    start(
        HttpClient.Version.HTTP_2,
        request -> {
          request
              .response()
              .exceptionHandler(
                  error -> {
                    if (responseExceptionCount.getAndIncrement() == 0) {
                      testing.runWithSpan("response exception", () -> {});
                      responseException.complete(null);
                    }
                  });
          request.endHandler(
              ignored -> vertx.runOnContext(ignored2 -> inboundEnded.complete(null)));
        });
    io.vertx.core.http.HttpClient client =
        vertx.createHttpClient(
            new HttpClientOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setTrustOptions(
                    new PfxOptions()
                        .setPath(System.getProperty("test.keystore"))
                        .setPassword("testing")));
    try {
      HttpClientRequest request =
          client
              .request(
                  new RequestOptions()
                      .setHost("localhost")
                      .setPort(port)
                      .setURI("/reset-after-end")
                      .setMethod(HttpMethod.POST))
              .toCompletionStage()
              .toCompletableFuture()
              .get(10, SECONDS);
      request.putHeader("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01");
      request.end("request body").toCompletionStage().toCompletableFuture().get(10, SECONDS);
      inboundEnded.get(10, SECONDS);
      assertThat(request.reset(8)).isTrue();
      responseException.get(10, SECONDS);
    } finally {
      client.close();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(ERROR)),
                span -> span.hasName("response exception").hasParent(trace.getSpan(0))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void resetDuringHeaderPreparation() throws Exception {
    CompletableFuture<Boolean> resetAccepted = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request ->
                request
                    .response()
                    .headersEndHandler(
                        ignored -> resetAccepted.complete(request.response().reset(8)))
                    .end());
    CompletableFuture<HttpResponse<String>> response = send(client, "/reset", TRACE_ID);
    boolean accepted = resetAccepted.get(10, SECONDS);
    if (accepted) {
      assertThatThrownBy(() -> response.get(10, SECONDS)).isInstanceOf(ExecutionException.class);
    } else {
      assertThat(response.get(10, SECONDS).statusCode()).isEqualTo(200);
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(accepted ? ERROR : UNSET))));
    assertDuration(SCOPE, 1);
  }

  @Test
  void resetDuringTrailerHeaderPreparation() throws Exception {
    CompletableFuture<Boolean> resetAccepted = new CompletableFuture<>();
    HttpClient client =
        start(
            HttpClient.Version.HTTP_2,
            request ->
                request
                    .response()
                    .headersEndHandler(
                        ignored -> {
                          request.response().trailers().set("x-finished", "yes");
                          resetAccepted.complete(request.response().reset(8));
                        })
                    .end("response body"));
    CompletableFuture<HttpResponse<String>> response = send(client, "/reset", TRACE_ID);
    boolean accepted = resetAccepted.get(10, SECONDS);
    if (accepted) {
      assertThatThrownBy(() -> response.get(10, SECONDS)).isInstanceOf(ExecutionException.class);
    } else {
      assertThat(response.get(10, SECONDS).statusCode()).isEqualTo(200);
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SERVER)
                        .hasTraceId(TRACE_ID)
                        .hasParentSpanId(PARENT_ID)
                        .hasStatusSatisfying(status -> status.hasCode(accepted ? ERROR : UNSET))));
    assertDuration(SCOPE, 1);
  }

  private static void assertDuration(String scope, int count) {
    testing.waitAndAssertMetrics(
        scope,
        "http.server.request.duration",
        metrics -> {
          metrics.allSatisfy(
              metric -> {
                assertThat(metric.getUnit()).isEqualTo("s");
                assertThat(metric.getHistogramData().getPoints())
                    .allSatisfy(point -> assertThat(point.getSum()).isPositive());
              });
          // The test exporter uses delta temporality; requests may land in different exports.
          metrics.satisfies(
              data ->
                  assertThat(
                          data.stream()
                              .flatMap(metric -> metric.getHistogramData().getPoints().stream())
                              .mapToLong(point -> point.getCount())
                              .sum())
                      .isEqualTo(count));
        });
  }

  private CompletableFuture<HttpClientResponse> sendAndPause(
      io.vertx.core.http.HttpClient client,
      String path,
      String traceId,
      AtomicInteger responseBytes,
      AtomicInteger responseCount,
      CompletableFuture<Void> responsesEnded) {
    return client
        .request(
            new RequestOptions()
                .setHost("localhost")
                .setPort(port)
                .setURI(path)
                .setMethod(HttpMethod.GET))
        .toCompletionStage()
        .toCompletableFuture()
        .thenCompose(
            request -> {
              request
                  .putHeader("traceparent", "00-" + traceId + "-" + PARENT_ID + "-01")
                  .putHeader("user-agent", "test-client");
              return request.send().toCompletionStage().toCompletableFuture();
            })
        .thenApply(
            response -> {
              response.handler(buffer -> responseBytes.addAndGet(buffer.length()));
              response.endHandler(
                  ignored -> {
                    if (responseCount.incrementAndGet() == 2) {
                      responsesEnded.complete(null);
                    }
                  });
              response.exceptionHandler(responsesEnded::completeExceptionally);
              response.pause();
              return response;
            });
  }

  private HttpClientRequest openStreamingRequest(
      io.vertx.core.http.HttpClient client, String path, String traceId) throws Exception {
    HttpClientRequest request =
        client
            .request(
                new RequestOptions()
                    .setHost("localhost")
                    .setPort(port)
                    .setURI(path)
                    .setMethod(HttpMethod.POST))
            .toCompletionStage()
            .toCompletableFuture()
            .get(10, SECONDS);
    request.putHeader("traceparent", "00-" + traceId + "-" + PARENT_ID + "-01").setChunked(true);
    request.write("request body").toCompletionStage().toCompletableFuture().get(10, SECONDS);
    return request;
  }
}
