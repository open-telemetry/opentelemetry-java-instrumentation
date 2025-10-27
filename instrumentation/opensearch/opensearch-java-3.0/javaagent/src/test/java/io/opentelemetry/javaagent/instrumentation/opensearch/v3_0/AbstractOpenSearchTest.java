/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.URI;
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

  protected abstract OpenSearchClient buildOpenSearchClient() throws Exception;

  protected abstract OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception;

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
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "GET"),
                                equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health")),
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
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
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
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "GET"),
                                equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health")),
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
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldRecordMetrics() throws IOException {
    HealthResponse healthResponse = openSearchClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    getTesting().waitForTraces(1);

    assertDurationMetric(
        getTesting(), "io.opentelemetry.opensearch-java-3.0", DB_OPERATION_NAME, DB_SYSTEM_NAME);
  }
}
