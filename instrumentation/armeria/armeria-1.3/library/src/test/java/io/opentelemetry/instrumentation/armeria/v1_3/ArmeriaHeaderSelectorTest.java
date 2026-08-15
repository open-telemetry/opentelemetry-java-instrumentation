/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHeaderSelectorTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private Server server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop().join();
    }
  }

  @Test
  void clientCapturesHeadersMatchingSelectors() {
    int port = startServer(UnaryOperator.identity());

    WebClient client =
        WebClient.builder("http://localhost:" + port)
            .decorator(
                ArmeriaClientTelemetry.builder(testing.getOpenTelemetry())
                    .setRequestHeaders(IncludeExclude.builder().setExcluded("x-ignored-*").build())
                    .setResponseHeaders(IncludeExclude.builder().setIncluded("x-test-*").build())
                    .build()
                    .createDecorator())
            .build();

    sendRequest(client);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttribute(
                            stringArrayKey("http.request.header.x-test-request"),
                            singletonList("request-value"))
                        .hasAttribute(
                            stringArrayKey("http.response.header.x-test-response"),
                            singletonList("response-value"))
                        // pseudo-headers are not HTTP headers
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getAttributes().asMap())
                                    .doesNotContainKey(
                                        stringArrayKey("http.request.header.:method")))));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void clientCapturesHeadersFromDeprecatedSetters() {
    int port = startServer(UnaryOperator.identity());

    WebClient client =
        WebClient.builder("http://localhost:" + port)
            .decorator(
                ArmeriaClientTelemetry.builder(testing.getOpenTelemetry())
                    .setCapturedRequestHeaders(singletonList("x-test-request"))
                    .setCapturedResponseHeaders(singletonList("x-test-response"))
                    .build()
                    .createDecorator())
            .build();

    sendRequest(client);

    assertCapturedHeaders();
  }

  @Test
  void serverCapturesHeadersMatchingSelectors() {
    int port =
        startServer(
            sb ->
                sb.decorator(
                    ArmeriaServerTelemetry.builder(testing.getOpenTelemetry())
                        .setRequestHeaders(IncludeExclude.builder().setIncluded("x-test-*").build())
                        .setResponseHeaders(
                            IncludeExclude.builder().setExcluded("content-*").build())
                        .build()
                        .createDecorator()));

    sendRequest(WebClient.of("http://localhost:" + port));

    assertCapturedHeaders();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void serverCapturesHeadersFromDeprecatedSetters() {
    int port =
        startServer(
            sb ->
                sb.decorator(
                    ArmeriaServerTelemetry.builder(testing.getOpenTelemetry())
                        .setCapturedRequestHeaders(singletonList("x-test-request"))
                        .setCapturedResponseHeaders(singletonList("x-test-response"))
                        .build()
                        .createDecorator()));

    sendRequest(WebClient.of("http://localhost:" + port));

    assertCapturedHeaders();
  }

  private int startServer(UnaryOperator<ServerBuilder> customizer) {
    ServerBuilder sb = Server.builder();
    sb.http(0);
    sb.service(
        "/test",
        (ctx, req) ->
            HttpResponse.of(
                ResponseHeaders.builder(HttpStatus.OK)
                    .add("x-test-response", "response-value")
                    .build(),
                HttpData.ofUtf8("success")));

    server = customizer.apply(sb).build();
    server.start().join();
    return server.activeLocalPort();
  }

  private static void sendRequest(WebClient client) {
    AggregatedHttpResponse response =
        client
            .execute(
                RequestHeaders.builder(HttpMethod.GET, "/test")
                    .add("x-test-request", "request-value")
                    .build())
            .aggregate()
            .join();

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
  }

  private static void assertCapturedHeaders() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttribute(
                            stringArrayKey("http.request.header.x-test-request"),
                            singletonList("request-value"))
                        .hasAttribute(
                            stringArrayKey("http.response.header.x-test-response"),
                            singletonList("response-value"))));
  }
}
