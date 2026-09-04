/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
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
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OPENSEARCH;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseListener;
import org.opensearch.client.RestClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOpenSearchRestTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected OpensearchContainer opensearch;
  protected RestClient client;
  protected URI httpHost;
  private String socketPeerAddress;

  protected abstract InstrumentationExtension getTesting();

  protected abstract RestClient buildRestClient(String... hostAddresses) throws Exception;

  protected abstract void resetNodes(RestClient client, String... hostAddresses) throws Exception;

  protected abstract int getResponseStatus(Response response);

  protected abstract String getResponseAddress(Response response);

  protected abstract String getInstrumentationName();

  @BeforeAll
  void setUp() throws Exception {
    opensearch =
        new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:1.3.6"))
            .withSecurityEnabled();
    cleanup.deferAfterAll(opensearch::stop);
    // limit memory usage and disable Log4j JMX to avoid cgroup detection issues in containers
    opensearch.withEnv(
        "OPENSEARCH_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    opensearch.start();
    httpHost = URI.create(opensearch.getHttpHostAddress());
    socketPeerAddress = InetAddress.getByName(httpHost.getHost()).getHostAddress();

    client = buildRestClient(opensearch.getHttpHostAddress());
    cleanup.deferAfterAll(client);
  }

  @Test
  void shouldGetStatusWithTraces() throws IOException {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    assertThat(getResponseStatus(response)).isEqualTo(200);
    String responseAddress = getResponseAddress(response);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(openSearchSpanName())
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(openSearchAttributes(responseAddress)),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
  }

  @Test
  void shouldGetStatusAsyncWithTraces() throws Exception {
    AtomicReference<Response> requestResponse = new AtomicReference<>(null);
    AtomicReference<Exception> exception = new AtomicReference<>(null);
    CountDownLatch countDownLatch = new CountDownLatch(1);

    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
            getTesting()
                .runWithSpan(
                    "callback",
                    () -> {
                      requestResponse.set(response);
                      countDownLatch.countDown();
                    });
          }

          @Override
          public void onFailure(Exception e) {
            getTesting()
                .runWithSpan(
                    "callback",
                    () -> {
                      exception.set(e);
                      countDownLatch.countDown();
                    });
          }
        };

    getTesting()
        .runWithSpan(
            "client",
            () -> {
              client.performRequestAsync(new Request("GET", "_cluster/health"), responseListener);
            });
    assertThat(countDownLatch.await(10, SECONDS)).isTrue();

    if (exception.get() != null) {
      throw exception.get();
    }
    assertThat(getResponseStatus(requestResponse.get())).isEqualTo(200);
    String responseAddress = getResponseAddress(requestResponse.get());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("client").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(openSearchSpanName())
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(openSearchAttributes(responseAddress)),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldRecordMetrics() throws IOException {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    assertThat(getResponseStatus(response)).isEqualTo(200);

    getTesting().waitForTraces(1);

    assertDurationMetric(
        getTesting(),
        getInstrumentationName(),
        DB_OPERATION_NAME,
        DB_SYSTEM_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void configuredNodeListIsTheWholeTarget() throws Exception {
    RestClient nodeListClient =
        buildRestClient(opensearch.getHttpHostAddress(), alternateHostAddress());
    cleanup.deferCleanup(nodeListClient);

    Response response = nodeListClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(nodeList(), null, getResponseAddress(response));
  }

  @Test
  void theTargetDoesNotFollowLaterNodeChanges() throws Exception {
    RestClient singleNodeClient = buildRestClient(opensearch.getHttpHostAddress());
    cleanup.deferCleanup(singleNodeClient);
    // a client is given new nodes when it is sniffed; the configured target must not follow them
    resetNodes(singleNodeClient, opensearch.getHttpHostAddress(), alternateHostAddress());

    Response response = singleNodeClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(
        httpHost.getHost(), Long.valueOf(httpHost.getPort()), getResponseAddress(response));
  }

  private String alternateHostAddress() {
    return httpHost.getScheme() + "://127.0.0.1:" + httpHost.getPort();
  }

  private String nodeList() {
    return httpHost.getHost() + ":" + httpHost.getPort() + ",127.0.0.1:" + httpHost.getPort();
  }

  private String openSearchSpanName() {
    return openSearchSpanName(httpHost.getHost(), Long.valueOf(httpHost.getPort()));
  }

  private static String openSearchSpanName(String serverAddress, Long serverPort) {
    // the stable span name falls back to the target, because opensearch has no namespace or
    // collection to name
    return emitStableDatabaseSemconv()
        ? "GET " + serverAddress + (serverPort != null ? ":" + serverPort : "")
        : "GET";
  }

  private List<AttributeAssertion> openSearchAttributes(String responseAddress) {
    return openSearchAttributes(
        emitStableDatabaseSemconv() ? httpHost.getHost() : null,
        emitStableDatabaseSemconv() ? Long.valueOf(httpHost.getPort()) : null,
        responseAddress);
  }

  private List<AttributeAssertion> openSearchAttributes(
      String serverAddress, Long serverPort, String responseAddress) {
    String expectedPeerAddress = emitStableDatabaseSemconv() ? socketPeerAddress : responseAddress;
    return asList(
        equalTo(maybeStable(DB_SYSTEM), OPENSEARCH),
        equalTo(maybeStable(DB_OPERATION), "GET"),
        equalTo(maybeStable(DB_STATEMENT), "GET _cluster/health"),
        equalTo(NETWORK_PEER_ADDRESS, expectedPeerAddress),
        equalTo(
            NETWORK_PEER_PORT,
            emitStableDatabaseSemconv() ? Long.valueOf(httpHost.getPort()) : null),
        equalTo(
            NETWORK_TYPE,
            emitOldDatabaseSemconv() && expectedPeerAddress != null
                ? (expectedPeerAddress.contains(":") ? "ipv6" : "ipv4")
                : null),
        equalTo(SERVER_ADDRESS, serverAddress),
        equalTo(SERVER_PORT, serverPort));
  }

  private void assertConfiguredTarget(
      String serverAddress, Long serverPort, String responseAddress) {
    getTesting()
        .waitAndAssertTraces(
            trace ->
                assertThat(trace.getSpan(0))
                    .hasName(openSearchSpanName(serverAddress, serverPort))
                    .hasKind(SpanKind.CLIENT)
                    .hasAttributesSatisfyingExactly(
                        openSearchAttributes(
                            emitStableDatabaseSemconv() ? serverAddress : null,
                            emitStableDatabaseSemconv() ? serverPort : null,
                            responseAddress)));
  }
}
