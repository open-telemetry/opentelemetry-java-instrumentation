/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Version;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.assertj.core.api.AbstractLongAssert;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class ElasticsearchClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;

  static ElasticsearchClient client;
  static ElasticsearchAsyncClient asyncClient;

  @BeforeAll
  static void setUp() {
    elasticsearch =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.2");
    // limit memory usage
    elasticsearch.withEnv("ES_JAVA_OPTS", "-Xmx256m -Xms256m");
    elasticsearch.start();

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress());

    RestClient restClient =
        RestClient.builder(httpHost)
            .setRequestConfigCallback(
                builder ->
                    builder
                        .setConnectTimeout(Integer.MAX_VALUE)
                        .setSocketTimeout(Integer.MAX_VALUE))
            .build();

    ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());
    client = new ElasticsearchClient(transport);
    asyncClient = new ElasticsearchAsyncClient(transport);
  }

  @AfterAll
  static void cleanUp() {
    elasticsearch.stop();
  }

  private static String userAgent() {
    return "elastic-java/" + Version.VERSION + " (Java/" + System.getProperty("java.version") + ")";
  }

  @Test
  public void elasticsearchStatus() throws IOException {
    InfoResponse response = client.info();
    Assertions.assertEquals(response.version().number(), "7.17.2");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("info")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                            equalTo(SemanticAttributes.DB_OPERATION, "info"),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                            equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort())),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                            equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
                            equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent()),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isPositive))));
  }

  @Test
  public void elasticsearchIndex() throws IOException {
    client.index(
        r ->
            r.id("test-id")
                .index("test-index")
                .document(new Person("person-name"))
                .timeout(t -> t.time("10s")));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("index")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                            equalTo(SemanticAttributes.DB_OPERATION, "index"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                            equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "PUT"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                httpHost.toURI() + "/test-index/_doc/test-id?timeout=10s"),
                            equalTo(
                                AttributeKey.stringKey("db.elasticsearch.path_parts.index"),
                                "test-index"),
                            equalTo(
                                AttributeKey.stringKey("db.elasticsearch.path_parts.id"),
                                "test-id")),
                span ->
                    span.hasName("PUT")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                            equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "PUT"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                httpHost.toURI() + "/test-index/_doc/test-id?timeout=10s"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 201L),
                            equalTo(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent()),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isPositive))));
  }

  @Test
  public void elasticsearchStatusAsync() throws Exception {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    AsyncRequest request = new AsyncRequest();

    runWithSpan(
        "parent",
        () ->
            asyncClient
                .info()
                .thenAccept(
                    infoResponse ->
                        runWithSpan(
                            "callback",
                            () -> {
                              request.setResponse(infoResponse);
                              countDownLatch.countDown();
                            })));
    //noinspection ResultOfMethodCallIgnored
    countDownLatch.await(10, TimeUnit.SECONDS);

    Assertions.assertEquals(request.getResponse().version().number(), "7.17.2");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("info")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                            equalTo(SemanticAttributes.DB_OPERATION, "info"),
                            equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                            equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                            equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
                            equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent()),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isPositive)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static class AsyncRequest {
    volatile InfoResponse response = null;

    public InfoResponse getResponse() {
      return response;
    }

    public void setResponse(InfoResponse response) {
      this.response = response;
    }
  }

  private static class Person {
    public final String name;

    Person(String name) {
      this.name = name;
    }

    @SuppressWarnings("unused")
    public String getName() {
      return name;
    }
  }
}
