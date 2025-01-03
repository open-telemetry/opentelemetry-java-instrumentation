/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.client;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.ratpack.v1_7.AbstractRatpackTest;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.http.client.HttpClient;
import ratpack.test.embed.EmbeddedApp;

class InstrumentedHttpClientTest extends AbstractRatpackTest {

  @Test
  void testPropagateTraceWithHttpCalls() throws Exception {
    EmbeddedApp otherApp =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(bindings -> serverTelemetry.configureRegistry(bindings)));
              spec.handlers(chain -> chain.get("bar", ctx -> ctx.render("foo")));
            });

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class, telemetry.instrument(HttpClient.of(Action.noop())));
                      }));

              spec.handlers(
                  chain ->
                      chain.get(
                          "foo",
                          ctx -> {
                            HttpClient instrumentedHttpClient = ctx.get(HttpClient.class);
                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress().toString() + "bar"))
                                .then(response -> ctx.render("bar"));
                          }));
            });

    app.test(
        httpClient -> {
          assertThat(httpClient.get("foo").getBody().getText()).isEqualTo("bar");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData = findSpanData("GET /foo", SpanKind.SERVER);
                    SpanData spanClientData = findSpanData("GET", SpanKind.CLIENT);
                    SpanData spanDataApi = findSpanData("GET /bar", SpanKind.SERVER);

                    assertThat(spanData.getTraceId()).isEqualTo(spanClientData.getTraceId());

                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(spanClientData.getKind()).isEqualTo(SpanKind.CLIENT);

                    Map<AttributeKey<?>, Object> clientAttributes =
                        spanClientData.getAttributes().asMap();
                    assertThat(clientAttributes.get(HTTP_ROUTE)).isEqualTo("/bar");
                    assertThat(clientAttributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(clientAttributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/foo");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/foo");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);

                    Map<AttributeKey<?>, Object> apiAttributes =
                        spanDataApi.getAttributes().asMap();
                    assertThat(apiAttributes.get(HTTP_ROUTE)).isEqualTo("/bar");
                    assertThat(apiAttributes.get(URL_PATH)).isEqualTo("/bar");
                    assertThat(apiAttributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(apiAttributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }

  @Test
  void testAddSpansForMultipleConcurrentClientCalls() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);

    EmbeddedApp otherApp =
        EmbeddedApp.of(
            spec ->
                spec.handlers(
                    chain -> {
                      chain.get("foo", ctx -> ctx.render("bar"));
                      chain.get("bar", ctx -> ctx.render("foo"));
                    }));

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class, telemetry.instrument(HttpClient.of(Action.noop())));
                      }));
              spec.handlers(
                  chain ->
                      chain.get(
                          "path-name",
                          ctx -> {
                            ctx.render("hello");
                            HttpClient instrumentedHttpClient = ctx.get(HttpClient.class);
                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress().toString() + "foo"))
                                .then(response -> latch.countDown());
                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress().toString() + "bar"))
                                .then(response -> latch.countDown());
                          }));
            });

    app.test(
        httpClient -> {
          assertThat(httpClient.get("path-name").getBody().getText()).isEqualTo("hello");
          latch.await(1, TimeUnit.SECONDS);

          await()
              .untilAsserted(
                  () -> {
                    assertThat(spanExporter.getFinishedSpanItems().size()).isEqualTo(3);

                    SpanData spanData = findSpanData("GET /path-name", SpanKind.SERVER);

                    SpanData spanClientData1 =
                        spanExporter.getFinishedSpanItems().stream()
                            .filter(
                                span ->
                                    "GET".equals(span.getName())
                                        && span.getAttributes()
                                            .asMap()
                                            .get(HTTP_ROUTE)
                                            .equals("/foo"))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Span not found"));

                    SpanData spanClientData2 =
                        spanExporter.getFinishedSpanItems().stream()
                            .filter(
                                span ->
                                    "GET".equals(span.getName())
                                        && span.getAttributes()
                                            .asMap()
                                            .get(HTTP_ROUTE)
                                            .equals("/bar"))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Span not found"));

                    assertThat(spanData.getTraceId()).isEqualTo(spanClientData1.getTraceId());
                    assertThat(spanData.getTraceId()).isEqualTo(spanClientData2.getTraceId());

                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);

                    assertThat(spanClientData1.getKind()).isEqualTo(SpanKind.CLIENT);
                    Map<AttributeKey<?>, Object> clientAttributes =
                        spanClientData1.getAttributes().asMap();
                    assertThat(clientAttributes.get(HTTP_ROUTE)).isEqualTo("/foo");
                    assertThat(clientAttributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(clientAttributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);

                    assertThat(spanClientData2.getKind()).isEqualTo(SpanKind.CLIENT);
                    Map<AttributeKey<?>, Object> client2Attributes =
                        spanClientData2.getAttributes().asMap();
                    assertThat(client2Attributes.get(HTTP_ROUTE)).isEqualTo("/bar");
                    assertThat(client2Attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(client2Attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/path-name");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/path-name");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }

  @Test
  void testHandlingExceptionErrorsInHttpClient() throws Exception {

    EmbeddedApp otherApp =
        EmbeddedApp.of(
            spec ->
                spec.handlers(
                    chain ->
                        chain.get(
                            "foo",
                            ctx ->
                                Promise.value("bar")
                                    .defer(Duration.ofSeconds(1L))
                                    .then(value -> ctx.render("bar")))));

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class,
                            telemetry.instrument(
                                HttpClient.of(s -> s.readTimeout(Duration.ofMillis(10)))));
                      }));

              spec.handlers(
                  chain ->
                      chain.get(
                          "path-name",
                          ctx -> {
                            HttpClient instrumentedHttpClient = ctx.get(HttpClient.class);
                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress().toString() + "foo"))
                                .onError(throwable -> ctx.render("error"))
                                .then(response -> ctx.render("hello"));
                          }));
            });

    app.test(
        httpClient -> {
          assertThat(httpClient.get("path-name").getBody().getText()).isEqualTo("error");

          await()
              .untilAsserted(
                  () -> {
                    SpanData spanData = findSpanData("GET /path-name", SpanKind.SERVER);
                    SpanData spanClientData = findSpanData("GET", SpanKind.CLIENT);

                    assertThat(spanData.getTraceId()).isEqualTo(spanClientData.getTraceId());

                    assertThat(spanData.getKind()).isEqualTo(SpanKind.SERVER);
                    assertThat(spanClientData.getKind()).isEqualTo(SpanKind.CLIENT);
                    Map<AttributeKey<?>, Object> clientAttributes =
                        spanClientData.getAttributes().asMap();
                    assertThat(clientAttributes.get(HTTP_ROUTE)).isEqualTo("/foo");
                    assertThat(clientAttributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(clientAttributes.get(HTTP_RESPONSE_STATUS_CODE)).isNull();
                    assertThat(spanClientData.getStatus().getStatusCode())
                        .isEqualTo(StatusCode.ERROR);
                    assertThat(spanClientData.getEvents().stream().findFirst().get().getName())
                        .isEqualTo("exception");

                    Map<AttributeKey<?>, Object> attributes = spanData.getAttributes().asMap();
                    assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/path-name");
                    assertThat(attributes.get(URL_PATH)).isEqualTo("/path-name");
                    assertThat(attributes.get(HTTP_REQUEST_METHOD)).isEqualTo("GET");
                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE)).isEqualTo(200L);
                  });
        });
  }

  @Test
  void testPropagateHttpTraceInRatpackServicesWithComputeThread() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    EmbeddedApp otherApp =
        EmbeddedApp.of(spec -> spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("bar"))));

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class, telemetry.instrument(HttpClient.of(Action.noop())));
                        bindings.bindInstance(
                            new BarService(
                                latch, otherApp.getAddress().toString() + "foo", openTelemetry));
                      }));

              spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("bar")));
            });

    app.getAddress();
    latch.await();
    await()
        .untilAsserted(
            () -> {
              SpanData spanData = findSpanData("a-span", SpanKind.INTERNAL);
              assertThat(
                      spanExporter.getFinishedSpanItems().stream()
                          .filter(span -> span.getTraceId().equals(spanData.getTraceId()))
                          .count())
                  .isEqualTo(3);
            });
  }

  @Test
  void propagateHttpTraceInRatpackServicesWithForkExecutions() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    EmbeddedApp otherApp =
        EmbeddedApp.of(spec -> spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("bar"))));

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class, telemetry.instrument(HttpClient.of(Action.noop())));
                        bindings.bindInstance(
                            new BarForkService(
                                latch, otherApp.getAddress().toString() + "foo", openTelemetry));
                      }));

              spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("bar")));
            });

    app.getAddress();
    latch.await();
    await()
        .untilAsserted(
            () -> {
              SpanData spanData = findSpanData("a-span", SpanKind.INTERNAL);
              assertThat(
                      spanExporter.getFinishedSpanItems().stream()
                          .filter(span -> span.getTraceId().equals(spanData.getTraceId()))
                          .count())
                  .isEqualTo(3);
            });
  }
}
