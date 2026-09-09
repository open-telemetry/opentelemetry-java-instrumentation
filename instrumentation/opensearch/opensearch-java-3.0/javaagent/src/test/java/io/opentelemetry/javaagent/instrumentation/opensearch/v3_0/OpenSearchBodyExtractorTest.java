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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.SpanKind;
import java.io.IOException;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

class OpenSearchBodyExtractorTest extends AbstractOpenSearchQueryTest {

  @Override
  protected OpenSearchTransport buildOpenSearchTransport(
      HttpHost host,
      PoolingAsyncClientConnectionManager connectionManager,
      BasicCredentialsProvider credentialsProvider) {
    JsonFactory jsonFactory =
        JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();
    JacksonJsonpMapper mapper = new JacksonJsonpMapper(new ObjectMapper(jsonFactory));

    return ApacheHttpClient5TransportBuilder.builder(host)
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                httpClientBuilder
                    .setConnectionManager(connectionManager)
                    .setDefaultCredentialsProvider(credentialsProvider))
        .setMapper(mapper)
        .build();
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void shouldUseJacksonMapperJsonFactory() throws IOException {
    String accentedCharacter = String.valueOf((char) 0xe9);
    SearchRequest searchRequest =
        SearchRequest.of(
            request ->
                request
                    .index(INDEX_NAME)
                    .query(
                        Query.of(
                            query ->
                                query.match(
                                    match ->
                                        match
                                            .field("m" + accentedCharacter + "ssage")
                                            .query(value -> value.stringValue("secret"))))));

    assertThat(openSearchClient.search(searchRequest, TestDocument.class)).isNotNull();

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
                                satisfies(
                                    maybeStable(DB_STATEMENT),
                                    val ->
                                        val.asString()
                                            .doesNotContain(accentedCharacter)
                                            .containsPattern("\\\\u00[eE]9")
                                            .contains("\"?\""))),
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
