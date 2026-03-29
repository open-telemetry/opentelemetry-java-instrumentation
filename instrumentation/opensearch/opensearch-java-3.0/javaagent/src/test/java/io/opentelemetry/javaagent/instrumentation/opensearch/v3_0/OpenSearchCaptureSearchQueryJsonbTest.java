/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
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
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import java.io.IOException;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

/**
 * This test verifies that the SanitizingJsonGenerator fallback path (non-Jackson mapper) correctly
 * sanitizes query bodies.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchCaptureSearchQueryJsonbTest extends AbstractOpenSearchQueryTest {

  @SuppressWarnings("deprecation") // RestClientTransport is deprecated
  @Override
  protected OpenSearchTransport buildOpenSearchTransport(
      HttpHost host,
      PoolingAsyncClientConnectionManager connectionManager,
      BasicCredentialsProvider credentialsProvider) {
    RestClient restClient =
        RestClient.builder(host)
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setConnectionManager(connectionManager))
            .build();

    return new RestClientTransport(restClient, new JsonbJsonpMapper());
  }

  @Test
  void shouldCaptureSearchQueryBodyWithJsonbMapper() throws IOException {
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
                                equalTo(maybeStablePeerService(), "test-peer-service"))));
  }
}
