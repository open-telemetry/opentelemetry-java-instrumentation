/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ELASTICSEARCH;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.http.HttpHost;
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
class ElasticsearchRest7Test {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;

  static RestClient client;

  static ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() {
    elasticsearch =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.10.2");
    cleanup.deferAfterAll(elasticsearch::stop);
    // limit memory usage
    elasticsearch.withEnv(
        "ES_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    elasticsearch.start();

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress());

    client =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(
                RestClient.builder(httpHost)
                    .setRequestConfigCallback(
                        builder ->
                            builder
                                .setConnectTimeout(Integer.MAX_VALUE)
                                .setSocketTimeout(Integer.MAX_VALUE)));
    cleanup.deferAfterAll(client);

    objectMapper = new ObjectMapper();
  }

  @Test
  void elasticsearchStatus() throws IOException {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    Map<?, ?> result = objectMapper.readValue(response.getEntity().getContent(), Map.class);
    assertThat(result.get("status")).isEqualTo("green");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? ELASTICSEARCH : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? null : httpHost.getHostName()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : Long.valueOf(httpHost.getPort())),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"))));
  }

  @Test
  void searchQueryCaptureFollowsV3Preview() throws IOException {
    Request request = new Request("POST", "/_search");
    request.setJsonEntity("{\"query\":{\"match\":{\"title\":\"secret user data\"}}}");

    Response response = client.performRequest(request);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? ELASTICSEARCH : "POST")
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
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? null : httpHost.getHostName()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : Long.valueOf(httpHost.getPort())),
                            equalTo(URL_FULL, httpHost.toURI() + "/_search"))));
  }

  @Test
  void elasticsearchStatusAsync() throws Exception {
    AsyncRequest asyncRequest = new AsyncRequest();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {

            runWithSpan(
                "callback",
                () -> {
                  asyncRequest.setRequestResponse(response);
                  countDownLatch.countDown();
                });
          }

          @Override
          public void onFailure(Exception e) {
            runWithSpan(
                "callback",
                () -> {
                  asyncRequest.setException(e);
                  countDownLatch.countDown();
                });
          }
        };

    runWithSpan(
        "parent",
        () -> client.performRequestAsync(new Request("GET", "_cluster/health"), responseListener));
    assertThat(countDownLatch.await(10, SECONDS)).isTrue();

    assertThat(asyncRequest.getException()).isNull();

    Map<?, ?> result =
        objectMapper.readValue(
            asyncRequest.getRequestResponse().getEntity().getContent(), Map.class);
    assertThat(result.get("status")).isEqualTo("green");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? ELASTICSEARCH : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? null : httpHost.getHostName()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : Long.valueOf(httpHost.getPort())),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static class AsyncRequest {
    private volatile Response requestResponse = null;
    private volatile Exception exception = null;

    Response getRequestResponse() {
      return requestResponse;
    }

    void setRequestResponse(Response requestResponse) {
      this.requestResponse = requestResponse;
    }

    Exception getException() {
      return exception;
    }

    void setException(Exception exception) {
      this.exception = exception;
    }
  }

  @Test
  void existingClientHasNoConfiguredTarget() throws IOException {
    RestClient wrappedClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(RestClient.builder(httpHost).build());
    cleanup.deferCleanup(wrappedClient);

    wrappedClient.performRequest(new Request("GET", "_cluster/health"));

    assertNoConfiguredTarget();
  }

  @Test
  void builderDoesNotUseRoutingNodesAsConfiguredTarget() throws IOException {
    RestClient wrappedClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(RestClient.builder(deadHost()));
    cleanup.deferCleanup(wrappedClient);
    wrappedClient.setNodes(asList(new Node(httpHost)));

    wrappedClient.performRequest(new Request("GET", "_cluster/health"));

    assertNoConfiguredTarget();
  }

  @Test
  void explicitSingleConfiguredTargetIsUsedForExistingClient() throws IOException {
    HttpHost configuredHost = new HttpHost("configured.example", 9300, "https");
    RestClient wrappedClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(RestClient.builder(httpHost).build(), configuredHost);
    cleanup.deferCleanup(wrappedClient);

    wrappedClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(configuredHost.getHostName(), configuredHost.getPort());
  }

  @Test
  void explicitMultipleConfiguredTargetsAreUsedForExistingClient() throws IOException {
    HttpHost secondConfiguredHost = new HttpHost("second.example", 9301, "https");
    RestClient wrappedClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(
                RestClient.builder(httpHost).build(),
                new HttpHost("first.example", 9300, "https"),
                secondConfiguredHost);
    cleanup.deferCleanup(wrappedClient);

    wrappedClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget("first.example:9300,second.example:9301", null);
  }

  @Test
  void explicitTargetDoesNotFollowLaterNodeChanges() throws IOException {
    HttpHost configuredHost = new HttpHost("configured.example", 9300, "https");
    RestClient wrappedClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(RestClient.builder(deadHost()).build(), configuredHost);
    cleanup.deferCleanup(wrappedClient);
    wrappedClient.setNodes(asList(new Node(httpHost)));

    wrappedClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(configuredHost.getHostName(), configuredHost.getPort());
  }

  private static HttpHost deadHost() {
    // nothing listens on this port, so it never answers a request
    return new HttpHost(httpHost.getHostName(), httpHost.getPort() + 1, httpHost.getSchemeName());
  }

  private static void assertNoConfiguredTarget() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? ELASTICSEARCH : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(
                                DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv() ? null : httpHost.getHostName()),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : Long.valueOf(httpHost.getPort())),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"))));
  }

  private static void assertConfiguredTarget(String address, Integer port) {
    boolean stableTarget = emitStableDatabaseSemconv();
    testing.waitAndAssertTraces(
        trace ->
            assertThat(trace.getSpan(0))
                .hasName(
                    emitStableDatabaseSemconv()
                        ? (port != null ? address + ":" + port : address)
                        : "GET")
                .hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? ELASTICSEARCH : null),
                    equalTo(DB_SYSTEM_NAME, emitStableDatabaseSemconv() ? ELASTICSEARCH : null),
                    equalTo(HTTP_REQUEST_METHOD, "GET"),
                    equalTo(SERVER_ADDRESS, stableTarget ? address : httpHost.getHostName()),
                    equalTo(
                        SERVER_PORT,
                        stableTarget
                            ? (port != null ? Long.valueOf(port) : null)
                            : Long.valueOf(httpHost.getPort())),
                    equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health")));
  }
}
