/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
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

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
                .setPfxKeyCertOptions(
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

  @ParameterizedTest
  @EnumSource(HttpClient.Version.class)
  void tlsServerMetrics(HttpClient.Version version) throws Exception {
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
  void headerOnlyResponseEndsBeforeUpload() throws Exception {
    CompletableFuture<Void> requestEnd = new CompletableFuture<>();
    CompletableFuture<Void> responseEnd = new CompletableFuture<>();
    start(
        HttpClient.Version.HTTP_2,
        request -> {
          assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
          request.endHandler(ignored -> requestEnd.complete(null));
          request.response().endHandler(ignored -> responseEnd.complete(null)).end();
        });
    io.vertx.core.http.HttpClient client =
        vertx.createHttpClient(
            new HttpClientOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setPfxTrustOptions(
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
                    return response.body();
                  })
              .toCompletionStage()
              .toCompletableFuture();
      request.write("unfinished upload").toCompletionStage().toCompletableFuture().get(10, SECONDS);
      assertThat(responseBody.get(10, SECONDS).length()).isZero();
      responseEnd.get(10, SECONDS);
      assertThat(requestEnd.isDone()).isFalse();
      assertDuration(SCOPE, 1);
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasKind(SERVER).hasTraceId(TRACE_ID).hasParentSpanId(PARENT_ID)));
      request.end().toCompletionStage().toCompletableFuture().get(10, SECONDS);
      requestEnd.get(10, SECONDS);
      assertDuration(SCOPE, 1);
    } finally {
      client.close();
    }
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
}
