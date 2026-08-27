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
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.MsearchRequest;
import org.opensearch.client.opensearch.core.MsearchResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

@SuppressWarnings("deprecation") // using deprecated semconv
class OpenSearchCaptureSearchQueryTest extends AbstractOpenSearchQueryTest {

  private static final int MAX_QUERY_BODY_LENGTH = 32 * 1024;
  private static final String JSON_PREFIX = "{\"query\":{\"match\":{\"";
  private static final String JSON_SUFFIX = "\":{\"query\":\"?\"}}}}";
  private static final String NDJSON_PREFIX = "{\"index\":[\"?\"]};" + JSON_PREFIX;

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
                                    val ->
                                        val.asString()
                                            .startsWith(httpHost + "/" + INDEX_NAME + "/_search")),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(maybeStablePeerService(), "test-peer-service"))));
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
    assertThat(msearchResponse.responses()).hasSizeGreaterThan(0);

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
                                    val ->
                                        val.asString()
                                            .startsWith(
                                                httpHost + "/" + "_msearch?typed_keys=true")),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(maybeStablePeerService(), "test-peer-service"))));
  }

  @Test
  void shouldKeepSearchQueryBodyAtLimit() throws IOException {
    String field = "a".repeat(MAX_QUERY_BODY_LENGTH - JSON_PREFIX.length() - JSON_SUFFIX.length());
    String expected = JSON_PREFIX + field + JSON_SUFFIX;

    openSearchClient.search(searchRequest(field), TestDocument.class);

    assertQueryBody(expected, "/" + INDEX_NAME + "/_search");
  }

  @Test
  void shouldTruncateSearchQueryBodyOverLimit() throws IOException {
    String field =
        "a".repeat(MAX_QUERY_BODY_LENGTH - JSON_PREFIX.length() - JSON_SUFFIX.length() + 1);
    String expected = JSON_PREFIX + field + JSON_SUFFIX;

    openSearchClient.search(searchRequest(field), TestDocument.class);

    assertQueryBody(expected.substring(0, MAX_QUERY_BODY_LENGTH), "/" + INDEX_NAME + "/_search");
  }

  @Test
  void shouldKeepMsearchQueryBodyAtLimit() throws IOException {
    String field =
        "a".repeat(MAX_QUERY_BODY_LENGTH - NDJSON_PREFIX.length() - JSON_SUFFIX.length());
    String expected = NDJSON_PREFIX + field + JSON_SUFFIX;

    openSearchClient.msearch(msearchRequest(field), TestDocument.class);

    assertQueryBody(expected, "/_msearch?typed_keys=true");
  }

  @Test
  void shouldTruncateMsearchQueryBodyOverLimit() throws IOException {
    String field =
        "a".repeat(MAX_QUERY_BODY_LENGTH - NDJSON_PREFIX.length() - JSON_SUFFIX.length() + 1);
    String expected = NDJSON_PREFIX + field + JSON_SUFFIX;

    openSearchClient.msearch(msearchRequest(field), TestDocument.class);

    assertQueryBody(expected.substring(0, MAX_QUERY_BODY_LENGTH), "/_msearch?typed_keys=true");
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
                                    val ->
                                        val.asString()
                                            .startsWith(httpHost + "/" + INDEX_NAME + "/_doc")),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 201L),
                                equalTo(maybeStablePeerService(), "test-peer-service"))));
  }

  private void assertQueryBody(String expected, String urlSuffix) {
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
                                equalTo(maybeStable(DB_STATEMENT), expected)),
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
                                    val -> val.asString().startsWith(httpHost + urlSuffix)),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(maybeStablePeerService(), "test-peer-service"))));
  }

  private static SearchRequest searchRequest(String field) {
    return SearchRequest.of(
        request ->
            request
                .index(INDEX_NAME)
                .query(
                    Query.of(
                        query ->
                            query.match(
                                match ->
                                    match
                                        .field(field)
                                        .query(value -> value.stringValue("value"))))));
  }

  private static MsearchRequest msearchRequest(String field) {
    return MsearchRequest.of(
        request ->
            request.searches(
                search ->
                    search
                        .header(header -> header.index(INDEX_NAME))
                        .body(body -> body.query(searchRequest(field).query()))));
  }
}
