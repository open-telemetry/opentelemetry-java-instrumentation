/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpRouteHolderTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @Mock HttpServerAttributesGetter<String, Void> getter;
  Instrumenter<String, Void> instrumenter;

  @BeforeEach
  void setUp() {
    instrumenter =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "test", s -> s)
            .addContextCustomizer(HttpRouteHolder.create(getter))
            .buildInstrumenter();
  }

  @Test
  void noLocalRootSpan() {
    Span parentSpan =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    parentSpan.end();

    Context context = instrumenter.start(Context.root().with(parentSpan), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertNull(HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(
            span -> assertThat(span).hasName("parent"), span -> assertThat(span).hasName("test"));
  }

  @Test
  void shouldSetRoute() {
    when(getter.getMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertEquals("/get/:id", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /get/:id"));
  }

  @Test
  void shouldNotUpdateRoute_sameSource() {
    when(getter.getMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/route1");
    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/route2");

    instrumenter.end(context, "test", null, null);

    assertEquals("/route1", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /route1"));
  }

  @Test
  void shouldNotUpdateRoute_lowerOrderSource() {
    when(getter.getMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.CONTROLLER, "/route1");
    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/route2");

    instrumenter.end(context, "test", null, null);

    assertEquals("/route1", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /route1"));
  }

  @Test
  void shouldUpdateRoute_higherOrderSource() {
    when(getter.getMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/route1");
    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.CONTROLLER, "/route2");

    instrumenter.end(context, "test", null, null);

    assertEquals("/route2", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /route2"));
  }

  @Test
  void shouldUpdateRoute_betterMatch() {
    when(getter.getMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.FILTER, "/a/route");
    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.FILTER, "/a/much/better/route");

    instrumenter.end(context, "test", null, null);

    assertEquals("/a/much/better/route", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /a/much/better/route"));
  }

  @Test
  void shouldNotUpdateRoute_worseMatch() {
    when(getter.getMethod("test")).thenReturn("GET");

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.FILTER, "/a/pretty/good/route");
    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.FILTER, "/a");

    instrumenter.end(context, "test", null, null);

    assertEquals("/a/pretty/good/route", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans())
        .satisfiesExactly(span -> assertThat(span).hasName("GET /a/pretty/good/route"));
  }

  @Test
  void shouldNotUpdateSpanName_noMethod() {
    when(getter.getMethod("test")).thenReturn(null);

    Context context = instrumenter.start(Context.root(), "test");
    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/get/:id");

    instrumenter.end(context, "test", null, null);

    assertEquals("/get/:id", HttpRouteHolder.getRoute(context));
    assertThat(testing.getSpans()).satisfiesExactly(span -> assertThat(span).hasName("test"));
  }
}
