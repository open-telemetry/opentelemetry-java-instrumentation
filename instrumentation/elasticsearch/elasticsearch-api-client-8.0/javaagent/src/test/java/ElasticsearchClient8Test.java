/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpHost;
import org.assertj.core.api.AbstractLongAssert;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import java.io.IOException;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

public class ElasticsearchClient8Test {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;

  static ElasticsearchClient client;

  @BeforeAll
  static void setUp() {
    elasticsearch = new ElasticsearchContainer(
        "docker.elastic.co/elasticsearch/elasticsearch:7.17.2");
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

    ElasticsearchTransport transport = new RestClientTransport(restClient,
        new JacksonJsonpMapper());
    client = new ElasticsearchClient(transport);
  }

  @AfterAll
  static void cleanUp() {
    elasticsearch.stop();
  }

  @Test
  // ignore deprecation interface
  public void elasticsearchStatus() throws IOException {
    InfoResponse response = client.info();
    Assertions.assertEquals(response.version().number(), "7.17.2");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("info")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                    equalTo(SemanticAttributes.DB_OPERATION, "info"),
                    equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                    equalTo(
                        SemanticAttributes.HTTP_URL, httpHost.toURI() + "/")
                ),
            span -> span.hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                    equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                    equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                    equalTo(AttributeKey.stringKey("net.protocol.name"), "http"),
                    equalTo(AttributeKey.stringKey("net.protocol.version"), "1.1"),
                    equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/"),
                    equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                    equalTo(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent()),
                    satisfies(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                        AbstractLongAssert::isPositive)
                )
        ));
  }

  @Test
  // ignore deprecation interface
  public void elasticsearchIndex() throws IOException {
    client.index(r ->
        r.id("test-id")
            .index("test-index")
            .document(new Person("person-name"))
            .timeout(t -> t.time("1s"))
    );

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("index")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                    equalTo(SemanticAttributes.DB_OPERATION, "index"),
                    equalTo(SemanticAttributes.HTTP_METHOD, "PUT"),
                    equalTo(
                        SemanticAttributes.HTTP_URL,
                        httpHost.toURI() + "/test-index/_doc/test-id?timeout=1s"),
                    equalTo(AttributeKey.stringKey("db.elasticsearch.path_parts.index"),
                        "test-index"),
                    equalTo(AttributeKey.stringKey("db.elasticsearch.path_parts.id"),
                        "test-id")
                ),
            span -> span.hasName("PUT")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                    equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                    equalTo(SemanticAttributes.HTTP_METHOD, "PUT"),
                    equalTo(AttributeKey.stringKey("net.protocol.name"), "http"),
                    equalTo(AttributeKey.stringKey("net.protocol.version"), "1.1"),
                    equalTo(SemanticAttributes.HTTP_URL,
                        httpHost.toURI() + "/test-index/_doc/test-id?timeout=1s"),
                    equalTo(SemanticAttributes.HTTP_STATUS_CODE, 201L),
                    equalTo(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent()),
                    satisfies(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                        AbstractLongAssert::isPositive)
                )
        ));
  }

  private static String userAgent() {
    return "elastic-java/" + RestClientBuilder.VERSION + " (Java/"
        + System.getProperty("java.version") + ")";
  }

  private static class Person {
    public final String name;

    Person(String name) {this.name = name;}

    @SuppressWarnings("unused")
    public String getName() {
      return name;
    }
  }
}
