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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.ratpack.v1_7.AbstractRatpackTest;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ratpack.exec.Blocking;
import ratpack.registry.Registry;
import ratpack.test.embed.EmbeddedApp;

class RatpackServerTest extends AbstractRatpackTest {

  @Test
  void testAddSpanOnHandlers() throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  registry -> Registry.of(regSpec -> serverTelemetry.configureRegistry(regSpec)));
              spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("hi-foo")));
            });

    app.test(
        httpClient -> {
          assertThat(httpClient.get("foo").getBody().getText()).isEqualTo("hi-foo");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData = findSpanData("GET /foo", SpanKind.SERVER);
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
  void testPropagateTraceWithInstrumentedAsyncOperations() throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  registry -> Registry.of(regSpec -> serverTelemetry.configureRegistry(regSpec)));
              spec.handlers(
                  chain ->
                      chain.get(
                          "foo",
                          ctx -> {
                            ctx.render("hi-foo");
                            Blocking.op(
                                    () -> {
                                      Span span =
                                          openTelemetry
                                              .getTracer("any-tracer")
                                              .spanBuilder("a-span")
                                              .startSpan();
                                      try (Scope scope = span.makeCurrent()) {
                                        span.addEvent("an-event");
                                      } finally {
                                        span.end();
                                      }
                                    })
                                .then();
                          }));
            });

    app.test(
        httpClient -> {
          assertThat(httpClient.get("foo").getBody().getText()).isEqualTo("hi-foo");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData = findSpanData("GET /foo", SpanKind.SERVER);
                    SpanData spanDataChild = findSpanData("a-span", SpanKind.INTERNAL);

                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(spanData.getTraceId()).isEqualTo(spanDataChild.getTraceId());
                    assertThat(spanDataChild.getParentSpanId()).isEqualTo(spanData.getSpanId());
                    assertThat(
                            spanDataChild.getEvents().stream()
                                .filter(event -> "an-event".equals(event.getName()))
                                .count())
                        .isEqualTo(1);

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/foo");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/foo");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }

  @Test
  void testPropagateTraceWithInstrumentedAsyncConcurrentOperations() throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  registry -> Registry.of(regSpec -> serverTelemetry.configureRegistry(regSpec)));
              spec.handlers(
                  chain -> {
                    chain.get(
                        "bar",
                        ctx -> {
                          ctx.render("hi-bar");
                          Blocking.op(
                                  () -> {
                                    Span span =
                                        openTelemetry
                                            .getTracer("any-tracer")
                                            .spanBuilder("another-span")
                                            .startSpan();
                                    try (Scope scope = span.makeCurrent()) {
                                      span.addEvent("an-event");
                                    } finally {
                                      span.end();
                                    }
                                  })
                              .then();
                        });
                    chain.get(
                        "foo",
                        ctx -> {
                          ctx.render("hi-foo");
                          Blocking.op(
                                  () -> {
                                    Span span =
                                        openTelemetry
                                            .getTracer("any-tracer")
                                            .spanBuilder("a-span")
                                            .startSpan();
                                    try (Scope scope = span.makeCurrent()) {
                                      span.addEvent("an-event");
                                    } finally {
                                      span.end();
                                    }
                                  })
                              .then();
                        });
                  });
            });

    app.test(
        httpClient -> {
          assertThat(httpClient.get("foo").getBody().getText()).isEqualTo("hi-foo");
          assertThat(httpClient.get("bar").getBody().getText()).isEqualTo("hi-bar");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData = findSpanData("GET /foo", SpanKind.SERVER);
                    SpanData spanDataChild = findSpanData("a-span", SpanKind.INTERNAL);
                    SpanData spanData2 = findSpanData("GET /bar", SpanKind.SERVER);
                    SpanData spanDataChild2 = findSpanData("another-span", SpanKind.INTERNAL);

                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(spanData.getTraceId()).isEqualTo(spanDataChild.getTraceId());
                    assertThat(spanDataChild.getParentSpanId()).isEqualTo(spanData.getSpanId());
                    assertThat(
                            spanDataChild.getEvents().stream()
                                .filter(event -> "an-event".equals(event.getName()))
                                .count())
                        .isEqualTo(1);

                    assertThat(spanData2.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(spanData2.getTraceId()).isEqualTo(spanDataChild2.getTraceId());
                    assertThat(spanDataChild2.getParentSpanId()).isEqualTo(spanData2.getSpanId());
                    assertThat(
                            spanDataChild2.getEvents().stream()
                                .filter(event -> "an-event".equals(event.getName()))
                                .count())
                        .isEqualTo(1);

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/foo");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/foo");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }
}
