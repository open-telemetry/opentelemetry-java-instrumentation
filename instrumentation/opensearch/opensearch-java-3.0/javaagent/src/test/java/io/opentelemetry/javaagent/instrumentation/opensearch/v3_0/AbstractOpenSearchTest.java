/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OPENSEARCH;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractOpenSearchTest {

  protected OpenSearchClient openSearchClient;
  protected OpenSearchAsyncClient openSearchAsyncClient;
  protected OpensearchContainer opensearch;
  protected URI httpHost;
  protected String peerAddress;

  protected abstract OpenSearchClient buildOpenSearchClient() throws Exception;

  protected abstract OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception;

  protected abstract boolean capturesActualPeer();

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @BeforeAll
  void setUp() throws Exception {
    opensearch =
        new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:1.3.6"))
            .withSecurityEnabled();
    // limit memory usage and disable Log4j JMX to avoid cgroup detection issues in containers
    opensearch.withEnv(
        "OPENSEARCH_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    opensearch.start();
    httpHost = URI.create(opensearch.getHttpHostAddress());
    peerAddress = InetAddress.getByName(httpHost.getHost()).getHostAddress();
    openSearchClient = buildOpenSearchClient();
    openSearchAsyncClient = buildOpenSearchAsyncClient();
  }

  @AfterAll
  void tearDown() {
    opensearch.stop();
  }

  @Test
  void shouldGetStatusWithTraces() throws IOException {
    HealthResponse healthResponse = openSearchClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(openSearchSpanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                withServer(
                                    equalTo(maybeStable(DB_SYSTEM), OPENSEARCH),
                                    equalTo(maybeStable(DB_OPERATION), "GET"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health"))),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(maybeStablePeerService(), "test-peer-service"))));
  }

  @Test
  void shouldGetStatusAsyncWithTraces() throws Exception {
    CountDownLatch countDownLatch = new CountDownLatch(1);

    CompletableFuture<HealthResponse> responseCompletableFuture =
        getTesting()
            .runWithSpan(
                "client",
                () ->
                    openSearchAsyncClient
                        .cluster()
                        .health()
                        .whenComplete(
                            (response, throwable) ->
                                getTesting().runWithSpan("callback", countDownLatch::countDown)));

    countDownLatch.await();
    HealthResponse healthResponse = responseCompletableFuture.get();
    assertThat(healthResponse).isNotNull();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("client").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(openSearchSpanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                withServer(
                                    equalTo(maybeStable(DB_SYSTEM), OPENSEARCH),
                                    equalTo(maybeStable(DB_OPERATION), "GET"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health"))),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(maybeStablePeerService(), "test-peer-service")),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldRecordMetrics() throws IOException {
    HealthResponse healthResponse = openSearchClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    getTesting().waitForTraces(1);

    if (capturesActualPeer()) {
      assertDurationMetric(
          getTesting(),
          "io.opentelemetry.opensearch-java-3.0",
          DB_OPERATION_NAME,
          DB_SYSTEM_NAME,
          NETWORK_PEER_ADDRESS,
          NETWORK_PEER_PORT,
          SERVER_ADDRESS,
          SERVER_PORT);
    } else {
      assertDurationMetric(
          getTesting(),
          "io.opentelemetry.opensearch-java-3.0",
          DB_OPERATION_NAME,
          DB_SYSTEM_NAME,
          SERVER_ADDRESS,
          SERVER_PORT);
    }
  }

  String openSearchSpanName(String method) {
    return emitStableDatabaseSemconv()
        ? method + " " + httpHost.getHost() + ":" + httpHost.getPort()
        : method;
  }

  List<AttributeAssertion> withServer(AttributeAssertion... assertions) {
    List<AttributeAssertion> result = new ArrayList<>(asList(assertions));
    result.add(equalTo(NETWORK_PEER_ADDRESS, capturesActualPeer() ? peerAddress : null));
    result.add(
        equalTo(NETWORK_PEER_PORT, capturesActualPeer() ? Long.valueOf(httpHost.getPort()) : null));
    if (emitStableDatabaseSemconv()) {
      result.add(equalTo(SERVER_ADDRESS, httpHost.getHost()));
      result.add(equalTo(SERVER_PORT, httpHost.getPort()));
    }
    return result;
  }

  void assertNodeListTarget() {
    String nodeList =
        httpHost.getHost()
            + ":"
            + (httpHost.getPort() + 1)
            + ","
            + httpHost.getHost()
            + ":"
            + httpHost.getPort();
    getTesting()
        .waitAndAssertTraces(
            trace ->
                assertThat(trace.getSpan(0))
                    .hasKind(SpanKind.CLIENT)
                    .hasAttributesSatisfying(
                        equalTo(NETWORK_PEER_ADDRESS, peerAddress),
                        equalTo(NETWORK_PEER_PORT, httpHost.getPort()),
                        equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? nodeList : null),
                        equalTo(SERVER_PORT, null)));
  }
}
