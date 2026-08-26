/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
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
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? httpHost.getHostName() + ":" + httpHost.getPort()
                                : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), ELASTICSEARCH),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"))));
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
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? httpHost.getHostName() + ":" + httpHost.getPort()
                                : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), ELASTICSEARCH),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
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
  void configuredNodeListIsTheWholeTarget() throws IOException {
    HttpHost deadHost = deadHost();
    RestClient nodeListClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(RestClient.builder(httpHost, deadHost));
    cleanup.deferCleanup(nodeListClient);

    nodeListClient.performRequest(new Request("GET", "_cluster/health"));

    assertConfiguredTarget(hostList(deadHost));
  }

  @Test
  void theTargetDoesNotFollowLaterNodeChanges() throws IOException {
    RestClient singleNodeClient =
        ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry())
            .wrap(RestClient.builder(httpHost));
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
                .hasAttributesSatisfying(
                    equalTo(SERVER_ADDRESS, stableHostList ? hostList : httpHost.getHostName()),
                    equalTo(
                        SERVER_PORT, stableHostList ? null : Long.valueOf(httpHost.getPort()))));
  }
}
