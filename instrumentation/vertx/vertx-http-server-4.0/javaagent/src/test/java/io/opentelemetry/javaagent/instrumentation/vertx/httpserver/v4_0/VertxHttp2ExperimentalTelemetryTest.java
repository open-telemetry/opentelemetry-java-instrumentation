/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static io.opentelemetry.semconv.incubating.HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.PfxOptions;
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
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxHttp2ExperimentalTelemetryTest {
  private static final String SCOPE = "io.opentelemetry.vertx-http-server-4.0";
  private static final boolean EXPERIMENTAL_TELEMETRY =
      Boolean.getBoolean("otel.instrumentation.http.server.emit-experimental-telemetry");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final Vertx vertx = Vertx.vertx();
  private final int port = PortUtils.findOpenPort();

  @AfterEach
  void close() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get(10, SECONDS);
  }

  @Test
  void experimentalServerTelemetry() throws Exception {
    CompletableFuture<HttpServerRequest> received = new CompletableFuture<>();
    HttpClient client = start(received);
    CompletableFuture<HttpResponse<String>> response =
        client.sendAsync(
            HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/body"))
                .timeout(Duration.ofSeconds(30))
                .header("user-agent", "test-client")
                .POST(HttpRequest.BodyPublishers.ofString("request body"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    HttpServerRequest request = received.get(10, SECONDS);
    assertThat(request.version()).isEqualTo(HttpVersion.HTTP_2);
    assertThat(request.isSSL()).isTrue();
    try {
      if (EXPERIMENTAL_TELEMETRY) {
        assertActiveRequests(1L);
      }
    } finally {
      vertx.runOnContext(
          ignored -> request.response().putHeader("content-length", "13").end("response body"));
    }
    HttpResponse<String> result = response.get(10, SECONDS);
    assertThat(result.version()).isEqualTo(HttpClient.Version.HTTP_2);
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("response body");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST")
                        .hasKind(SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(URL_SCHEME, "https"),
                            equalTo(URL_PATH, "/body"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(USER_AGENT_ORIGINAL, "test-client"),
                            satisfies(CLIENT_ADDRESS, val -> val.isNotBlank()),
                            satisfies(NETWORK_PEER_ADDRESS, val -> val.isNotBlank()),
                            satisfies(NETWORK_PEER_PORT, val -> val.isPositive()),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(HTTP_REQUEST_BODY_SIZE, experimental(12L)),
                            equalTo(HTTP_RESPONSE_BODY_SIZE, experimental(13L)))));
    testing.waitAndAssertMetrics(
        SCOPE,
        "http.server.request.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                                equalTo(URL_SCHEME, "https"),
                                                equalTo(NETWORK_PROTOCOL_VERSION, "2"))))));
    if (EXPERIMENTAL_TELEMETRY) {
      assertActiveRequests(1L, -1L);
      assertBodySize("http.server.request.body.size", 12);
      assertBodySize("http.server.response.body.size", 13);
    } else {
      assertThat(testing.metrics())
          .filteredOn(metric -> metric.getInstrumentationScopeInfo().getName().equals(SCOPE))
          .extracting(MetricData::getName)
          .containsExactly("http.server.request.duration");
    }
  }

  private HttpClient start(CompletableFuture<HttpServerRequest> received) throws Exception {
    vertx
        .createHttpServer(
            new HttpServerOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setKeyCertOptions(
                    new PfxOptions()
                        .setPath(System.getProperty("test.keystore"))
                        .setPassword("testing")))
        .requestHandler(
            request ->
                request.bodyHandler(
                    body -> {
                      assertThat(body.toString()).isEqualTo("request body");
                      received.complete(request);
                    }))
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
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).sslContext(ssl).build();
  }

  private static void assertActiveRequests(Long... deltas) {
    testing.waitAndAssertMetrics(
        SCOPE,
        "http.server.active_requests",
        metrics ->
            metrics
                .allSatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("{requests}")
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.isNotMonotonic()
                                        .hasPointsSatisfying(
                                            point ->
                                                point.hasAttributesSatisfyingExactly(
                                                    equalTo(HTTP_REQUEST_METHOD, "POST"),
                                                    equalTo(URL_SCHEME, "https")))))
                .flatExtracting(metric -> metric.getLongSumData().getPoints())
                .extracting(LongPointData::getValue)
                .containsExactly(deltas));
  }

  private static void assertBodySize(String name, int size) {
    testing.waitAndAssertMetrics(
        SCOPE,
        name,
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("By")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasSum(size)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                                equalTo(URL_SCHEME, "https"),
                                                equalTo(NETWORK_PROTOCOL_VERSION, "2"))))));
  }

  private static Long experimental(Long value) {
    return EXPERIMENTAL_TELEMETRY ? value : null;
  }
}
