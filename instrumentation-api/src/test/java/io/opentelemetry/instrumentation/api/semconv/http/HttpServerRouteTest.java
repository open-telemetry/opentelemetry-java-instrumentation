/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpServerRouteTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @Mock HttpServerAttributesGetter<String, Void> getter;
  Instrumenter<String, Void> instrumenter;

  @BeforeEach
  void setUp() {
    instrumenter =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "test", s -> s)
            .addContextCustomizer(
                HttpServerRoute.builder(getter)
                    .setKnownMethods(new HashSet<>(singletonList("GET")))
                    .build())
            .buildInstrumenter(s -> SpanKind.SERVER);
  }

  @Test
  void noLocalRootSpan() {
    Span parentSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    parentSpan.end();

    Context context = instrumenter.start(Context.root().with(parentSpan), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertNull(HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(
            span -> assertThat(span).hasName("parent"), span -> assertThat(span).hasName("test"));
  }

  @Test
  void nonServerRootSpan() {
    Instrumenter<String, Void> testInstrumenter =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "test", s -> s)
            .addContextCustomizer(
                HttpServerRoute.builder(getter)
                    .setKnownMethods(new HashSet<>(singletonList("GET")))
                    .build())
            .buildInstrumenter(s -> SpanKind.INTERNAL);

    Context context = testInstrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/get/:id");

    testInstrumenter.end(context, "test", null, null);

    assertNull(HttpServerRoute.get(context));
    assertThat(testing.getSpans()).satisfiesExactly(span -> assertThat(span).hasName("test"));
  }

  @Test
  void shouldSetRoute() {
    when(getter.getHttpRequestMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertEquals("/get/:id", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /get/:id"));
  }

  @Test
  void shouldNotUpdateRoute_sameSource() {
    when(getter.getHttpRequestMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/route1");
    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/route2");

    instrumenter.end(context, "test", null, null);

    assertEquals("/route1", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /route1"));
  }

  @Test
  void shouldNotUpdateRoute_lowerOrderSource() {
    when(getter.getHttpRequestMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, "/route1");
    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/route2");

    instrumenter.end(context, "test", null, null);

    assertEquals("/route1", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /route1"));
  }

  @Test
  void shouldUpdateRoute_higherOrderSource() {
    when(getter.getHttpRequestMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/route1");
    HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, "/route2");

    instrumenter.end(context, "test", null, null);

    assertEquals("/route2", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /route2"));
  }

  @Test
  void shouldUpdateRoute_betterMatch() {
    when(getter.getHttpRequestMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER_FILTER, "/a/route");
    HttpServerRoute.update(context, HttpServerRouteSource.SERVER_FILTER, "/a/much/better/route");

    instrumenter.end(context, "test", null, null);

    assertEquals("/a/much/better/route", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /a/much/better/route"));
  }

  @Test
  void shouldNotUpdateRoute_worseMatch() {
    when(getter.getHttpRequestMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER_FILTER, "/a/pretty/good/route");
    HttpServerRoute.update(context, HttpServerRouteSource.SERVER_FILTER, "/a");

    instrumenter.end(context, "test", null, null);

    assertEquals("/a/pretty/good/route", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /a/pretty/good/route"));
  }

  @Test
  void shouldUseHttp_noMethod() {
    when(getter.getHttpRequestMethod("test")).thenReturn(null);

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertEquals("/get/:id", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("HTTP /get/:id"));
  }

  @Test
  void shouldUseHttp_unknownMethod() {
    when(getter.getHttpRequestMethod("test")).thenReturn("POST");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpServerRoute.get(context));

    HttpServerRoute.update(context, HttpServerRouteSource.SERVER, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertEquals("/get/:id", HttpServerRoute.get(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("HTTP /get/:id"));
  }
}
