/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
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
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

class OpenSearchQuerySanitizationDisabledTest extends AbstractOpenSearchQueryTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void shouldCaptureUnsanitizedSearchQueryBody() throws IOException {
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
                        span.hasName(
                                emitStableDatabaseSemconv()
                                    ? "POST " + httpHost.getHost() + ":" + httpHost.getPort()
                                    : "POST")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "POST"),
                                equalTo(
                                    maybeStable(DB_STATEMENT),
                                    "{\"query\":{\"match\":{\"message\":{\"query\":\"test\"}}}}"),
                                equalTo(NETWORK_TYPE, null),
                                equalTo(
                                    SERVER_ADDRESS,
                                    emitStableDatabaseSemconv() ? httpHost.getHost() : null),
                                equalTo(
                                    SERVER_PORT,
                                    emitStableDatabaseSemconv()
                                        ? Long.valueOf(httpHost.getPort())
                                        : null)),
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
}
