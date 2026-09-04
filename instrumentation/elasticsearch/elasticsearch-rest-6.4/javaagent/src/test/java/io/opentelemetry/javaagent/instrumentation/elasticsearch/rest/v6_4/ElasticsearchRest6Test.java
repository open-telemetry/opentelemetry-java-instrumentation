/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v6_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ELASTICSEARCH;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class ElasticsearchRest6Test {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;
  static String peerAddress;

  static RestClient client;

  static ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() throws UnknownHostException {
    elasticsearch =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:6.8.16");
    // limit memory usage
    elasticsearch.withEnv(
        "ES_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    elasticsearch.start();
    cleanup.deferAfterAll(elasticsearch::stop);

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress());
    peerAddress = InetAddress.getByName(httpHost.getHostName()).getHostAddress();
    client =
        RestClient.builder(httpHost)
            .setMaxRetryTimeoutMillis(Integer.MAX_VALUE)
            .setRequestConfigCallback(
                builder ->
                    builder
                        .setConnectTimeout(Integer.MAX_VALUE)
                        .setSocketTimeout(Integer.MAX_VALUE))
            .build();
    cleanup.deferAfterAll(client);

    objectMapper = new ObjectMapper();
  }

  @Test
  void elasticsearchStatus() throws IOException {
    Response response = client.performRequest("GET", "_cluster/health");
    Map<?, ?> result =
        objectMapper.readValue(
            response.getEntity().getContent(), new TypeReference<Map<?, ?>>() {});

    assertThat(result.get("status")).isEqualTo("green");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? httpHost.getHostName() + ":" + httpHost.getPort()
                                : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? Long.valueOf(httpHost.getPort())
                                    : null),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.elasticsearch-rest-6.4",
        DB_SYSTEM_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void searchQueryCaptureFollowsV3Preview() throws IOException {
    Response response =
        client.performRequest(
            "POST",
            "_search",
            emptyMap(),
            new StringEntity(
                "{\"query\":{\"match\":{\"title\":\"secret user data\"}}}",
                ContentType.APPLICATION_JSON));

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? httpHost.getHostName() + ":" + httpHost.getPort()
                                : "POST")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_STATEMENT,
                                emitOldDatabaseSemconv() && v3Preview()
                                    ? "{\"query\":{\"match\":{\"title\":\"?\"}}}"
                                    : null),
                            equalTo(
                                DB_QUERY_TEXT,
                                emitStableDatabaseSemconv() && v3Preview()
                                    ? "{\"query\":{\"match\":{\"title\":\"?\"}}}"
                                    : null),
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? Long.valueOf(httpHost.getPort())
                                    : null),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(URL_FULL, httpHost.toURI() + "/_search")),
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(URL_FULL, httpHost.toURI() + "/_search"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
  }

  @Test
  void elasticsearchStatusAsync() throws Exception {
    Response[] requestResponse = {null};
    Exception[] exception = {null};
    CountDownLatch countDownLatch = new CountDownLatch(1);
    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
            testing.runWithSpan(
                "callback",
                () -> {
                  requestResponse[0] = response;
                  countDownLatch.countDown();
                });
          }

          @Override
          public void onFailure(Exception e) {
            testing.runWithSpan(
                "callback",
                () -> {
                  exception[0] = e;
                  countDownLatch.countDown();
                });
          }
        };
    testing.runWithSpan(
        "parent", () -> client.performRequestAsync("GET", "_cluster/health", responseListener));
    assertThat(countDownLatch.await(10, SECONDS)).isTrue();

    if (exception[0] != null) {
      throw exception[0];
    }
    Map<?, ?> result =
        objectMapper.readValue(
            requestResponse[0].getEntity().getContent(), new TypeReference<Map<?, ?>>() {});

    assertThat(result.get("status")).isEqualTo("green");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? httpHost.getHostName() + ":" + httpHost.getPort()
                                : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv() ? peerAddress : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? Long.valueOf(httpHost.getPort())
                                    : null),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void configuredNodeListIsTheWholeTarget() throws IOException {
    HttpHost deadHost = deadHost();
    RestClient nodeListClient =
        RestClient.builder(httpHost, deadHost).setMaxRetryTimeoutMillis(Integer.MAX_VALUE).build();
    cleanup.deferCleanup(nodeListClient);

    nodeListClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(hostList(deadHost));
  }

  @Test
  void theTargetDoesNotFollowLaterNodeChanges() throws IOException {
    RestClient singleNodeClient = RestClient.builder(httpHost).build();
    cleanup.deferCleanup(singleNodeClient);
    // a client is given new nodes when it is sniffed; the configured target must not follow them
    singleNodeClient.setNodes(asList(new Node(httpHost), new Node(deadHost())));

    singleNodeClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(null);
  }

  private static HttpHost deadHost() {
    // nothing listens on this port, so a request is served by the running server after a retry
    return new HttpHost(httpHost.getHostName(), httpHost.getPort() + 1, httpHost.getSchemeName());
  }

  private static String hostList(HttpHost deadHost) {
    return httpHost.getHostName()
        + ":"
        + httpHost.getPort()
        + ","
        + deadHost.getHostName()
        + ":"
        + deadHost.getPort();
  }

  private static void assertConfiguredTarget(String hostList) {
    boolean stableHostList = emitStableDatabaseSemconv() && hostList != null;
    testing.waitAndAssertTraces(
        trace ->
            assertThat(trace.getSpan(0))
                .hasName(
                    emitStableDatabaseSemconv()
                        ? (hostList != null
                            ? hostList
                            : httpHost.getHostName() + ":" + httpHost.getPort())
                        : "GET")
                .hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                    equalTo(DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                    equalTo(HTTP_REQUEST_METHOD, "GET"),
                    equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? peerAddress : null),
                    equalTo(
                        NETWORK_PEER_PORT,
                        emitStableDatabaseSemconv() ? Long.valueOf(httpHost.getPort()) : null),
                    equalTo(SERVER_ADDRESS, stableHostList ? hostList : httpHost.getHostName()),
                    equalTo(SERVER_PORT, stableHostList ? null : Long.valueOf(httpHost.getPort())),
                    equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health")));
  }
}
