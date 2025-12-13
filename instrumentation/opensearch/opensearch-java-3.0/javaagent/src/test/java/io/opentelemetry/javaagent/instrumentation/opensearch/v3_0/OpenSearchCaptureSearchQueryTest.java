/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.URI;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.MsearchRequest;
import org.opensearch.client.opensearch.core.MsearchResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tests for capture-search-query=true configuration. This test class runs with
 * -Dotel.instrumentation.opensearch.capture-search-query=true and verifies that query bodies are
 * captured in DB_STATEMENT.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchCaptureSearchQueryTest {

  private static final String INDEX_NAME = "test-search-index";
  private OpenSearchClient openSearchClient;
  private OpensearchContainer opensearch;
  private URI httpHost;

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
    opensearch.withEnv(
        "OPENSEARCH_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    opensearch.start();
    httpHost = URI.create(opensearch.getHttpHostAddress());
    openSearchClient = buildOpenSearchClient();

    String documentId = "test-doc-1";

    CreateIndexRequest createIndexRequest =
        CreateIndexRequest.of(
            c ->
                c.index(INDEX_NAME)
                    .mappings(
                        TypeMapping.of(
                            t ->
                                t.properties(
                                    "message",
                                    p ->
                                        p.text(txt -> txt.fielddata(true).analyzer("standard"))))));

    openSearchClient.indices().create(createIndexRequest);

    TestDocument testDocument = TestDocument.create(documentId, "test message for search");
    IndexRequest<TestDocument> indexRequest =
        new IndexRequest.Builder<TestDocument>().index(INDEX_NAME).document(testDocument).build();

    openSearchClient.index(indexRequest);
    openSearchClient.indices().refresh(r -> r.index(INDEX_NAME));
  }

  private OpenSearchClient buildOpenSearchClient() throws Exception {
    HttpHost host = new HttpHost("https", httpHost.getHost(), httpHost.getPort());

    TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    TlsStrategy tlsStrategy =
        ClientTlsStrategyBuilder.create()
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .setSslContext(sslContext)
            .build();
    PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            opensearch.getUsername(), opensearch.getPassword().toCharArray()));

    OpenSearchTransport apacheHttpClient5Transport =
        ApacheHttpClient5TransportBuilder.builder(host)
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setConnectionManager(connectionManager)
                        .setDefaultCredentialsProvider(credentialsProvider))
            .build();
    return new OpenSearchClient(apacheHttpClient5Transport);
  }

  @AfterAll
  void tearDown() {
    opensearch.stop();
  }

  @Test
  void shouldCaptureSearchQueryBody() throws IOException {
    SearchRequest searchRequest =
        SearchRequest.of(
            s ->
                s.index(INDEX_NAME)
                    .query(
                        Query.of(
                            q ->
                                q.match(
                                    m -> m.field("message").query(v -> v.stringValue("test"))))));

    SearchResponse<TestDocument> searchResponse =
        openSearchClient.search(searchRequest, TestDocument.class);
    assertThat(searchResponse.hits().total().value()).isGreaterThan(0);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "POST"),
                                equalTo(
                                    maybeStable(DB_STATEMENT),
                                    "{\"query\":{\"match\":{\"message\":{\"query\":\"?\"}}}}")),
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                satisfies(
                                    URL_FULL,
                                    url ->
                                        url.asString()
                                            .startsWith(httpHost + "/" + INDEX_NAME + "/_search")),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(PEER_SERVICE, "test-peer-service"))));
  }

  @Test
  void shouldCaptureMsearchQueryBody() throws IOException {
    MsearchRequest msearchRequest =
        new MsearchRequest.Builder()
            .searches(
                s ->
                    s.header(h -> h.index(INDEX_NAME))
                        .body(
                            b ->
                                b.query(
                                    q ->
                                        q.term(
                                            t ->
                                                t.field("message")
                                                    .value(v -> v.stringValue("message"))))))
            .searches(
                s ->
                    s.header(h -> h.index(INDEX_NAME))
                        .body(
                            b ->
                                b.query(
                                    q ->
                                        q.term(
                                            t ->
                                                t.field("message2")
                                                    .value(v -> v.longValue(100L))))))
            .searches(
                s ->
                    s.header(h -> h.index(INDEX_NAME))
                        .body(
                            b ->
                                b.query(
                                    q ->
                                        q.term(
                                            t ->
                                                t.field("message3")
                                                    .value(v -> v.booleanValue(true))))))
            .build();

    MsearchResponse<TestDocument> msearchResponse =
        openSearchClient.msearch(msearchRequest, TestDocument.class);
    assertThat(msearchResponse.responses().size()).isGreaterThan(0);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "POST"),
                                equalTo(
                                    maybeStable(DB_STATEMENT),
                                    "{\"index\":[\"?\"]};{\"query\":{\"term\":{\"message\":{\"value\":\"?\"}}}};{\"index\":[\"?\"]};{\"query\":{\"term\":{\"message2\":{\"value\":\"?\"}}}};{\"index\":[\"?\"]};{\"query\":{\"term\":{\"message3\":{\"value\":\"?\"}}}}")),
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                satisfies(
                                    URL_FULL,
                                    url ->
                                        url.asString()
                                            .startsWith(
                                                httpHost + "/" + "_msearch?typed_keys=true")),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(PEER_SERVICE, "test-peer-service"))));
  }

  @Test
  void shouldNotCaptureIndexQueryBody() throws IOException {
    TestDocument testDocument = TestDocument.create("test-doc-2", "index body test message");
    IndexRequest<TestDocument> indexRequest =
        new IndexRequest.Builder<TestDocument>().index(INDEX_NAME).document(testDocument).build();

    openSearchClient.index(indexRequest);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "POST"),
                                equalTo(maybeStable(DB_STATEMENT), "POST /test-search-index/_doc")),
                    span ->
                        span.hasName("POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                satisfies(
                                    URL_FULL,
                                    url ->
                                        url.asString()
                                            .startsWith(httpHost + "/" + INDEX_NAME + "/_doc")),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 201L),
                                equalTo(PEER_SERVICE, "test-peer-service"))));
  }
}
