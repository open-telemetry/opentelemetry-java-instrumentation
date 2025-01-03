/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RatpackServerApplicationTest {

  private final RatpackFunctionalTest app = new RatpackFunctionalTest(RatpackApp.class);

  @Test
  void testAddSpanOnHandlers() throws Exception {
    app.test(
        httpClient -> {
          assertThat(httpClient.get("foo").getBody().getText()).isEqualTo("hi-foo");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData =
                        app.spanExporter.getFinishedSpanItems().stream()
                            .filter(span -> "GET /foo".equals(span.getName()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Span not found"));

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();

                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/foo");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/foo");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }

  @Test
  void testPropagateTraceToHttpCalls() throws Exception {
    app.test(
        httpClient -> {
          assertThat(httpClient.get("bar").getBody().getText()).isEqualTo("hi-bar");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData =
                        app.spanExporter.getFinishedSpanItems().stream()
                            .filter(span -> "GET /bar".equals(span.getName()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Span not found"));

                    SpanData spanDataClient =
                        app.spanExporter.getFinishedSpanItems().stream()
                            .filter(span -> span.getName().equals("GET"))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Span not found"));

                    assertThat(spanData.getTraceId()).isEqualTo(spanDataClient.getTraceId());

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();
                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/bar");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/bar");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);

                    Map<AttributeKey<?>, Object> clientAttributes =
                        spanDataClient.getAttributes().asMap();

                    assertThat(spanDataClient.getKind()).isEqualTo(SpanKind.CLIENT);
                    assertThat(clientAttributes.get(HTTP_ROUTE)).isEqualTo("/other");
                    assertThat(clientAttributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(clientAttributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }

  @Test
  void testIgnoreHandlersBeforeOpenTelemetryServerHandler() throws Exception {
    app.test(
        httpClient -> {
          assertThat(httpClient.get("ignore").getBody().getText()).isEqualTo("ignored");

          await()
              .untilAsserted(
                  () ->
                      assertThat(
                              app.spanExporter.getFinishedSpanItems().stream()
                                  .noneMatch(span -> "GET /ignore".equals(span.getName())))
                          .isTrue());
        });
  }

  RatpackServerApplicationTest() throws Exception {}
}
