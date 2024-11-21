/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.testing.AgentSpanTesting;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ContextBridgeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testLocalRootSpanBridge() {
    AgentSpanTesting.runWithHttpServerSpan(
        "server",
        () -> {
          assertNotNull(Span.fromContextOrNull(Context.current()));
          assertNotNull(LocalRootSpan.fromContextOrNull(Context.current()));
          testing.runWithSpan(
              "internal", () -> assertNotNull(LocalRootSpan.fromContextOrNull(Context.current())));
        });
  }

  @Test
  void testSpanKeyBridge() {
    AgentSpanTesting.runWithAllSpanKeys(
        "parent",
        () -> {
          assertNotNull(Span.fromContextOrNull(Context.current()));

          List<SpanKey> spanKeys =
              Arrays.asList(
                  // span kind keys
                  SpanKey.KIND_SERVER,
                  SpanKey.KIND_CLIENT,
                  SpanKey.KIND_CONSUMER,
                  SpanKey.KIND_PRODUCER,
                  // semantic convention keys
                  SpanKey.HTTP_SERVER,
                  SpanKey.RPC_SERVER,
                  SpanKey.HTTP_CLIENT,
                  SpanKey.RPC_CLIENT,
                  SpanKey.DB_CLIENT,
                  SpanKey.PRODUCER,
                  SpanKey.CONSUMER_RECEIVE,
                  SpanKey.CONSUMER_PROCESS);

          spanKeys.forEach(spanKey -> assertNotNull(spanKey.fromContextOrNull(Context.current())));

          testing.runWithSpan(
              "internal",
              () ->
                  spanKeys.forEach(
                      spanKey -> assertNotNull(spanKey.fromContextOrNull(Context.current()))));
        });
  }

  @Test
  void testSpanKeyBridge_UnbridgedSpan() {
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder().build();
    // span is bridged only when it is created though a bridged OpenTelemetry instance obtained
    // from GlobalOpenTelemetry
    Span span = openTelemetry.getTracer("test").spanBuilder("test").startSpan();

    Context context = SpanKey.HTTP_CLIENT.storeInContext(Context.current(), span);
    assertThat(context).isNotNull();
    assertThat(SpanKey.HTTP_CLIENT.fromContextOrNull(context)).isEqualTo(span);
  }

  @Test
  void testHttpRouteHolder_SameSourceAsServerInstrumentationDoesNotOverrideRoute() {
    AgentSpanTesting.runWithHttpServerSpan(
        "server",
        () ->
            HttpServerRoute.update(
                Context.current(), HttpServerRouteSource.SERVER, "/test/controller/:id"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /test/server/*")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_ROUTE, "/test/server/*"),
                            equalTo(ERROR_TYPE, "_OTHER"))));
  }

  @Test
  void testHttpRouteHolder_SourceWithHigherOrderValueOverridesRoute() {
    AgentSpanTesting.runWithHttpServerSpan(
        "server",
        () ->
            HttpServerRoute.update(
                Context.current(), HttpServerRouteSource.CONTROLLER, "/test/controller/:id"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /test/controller/:id")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_ROUTE, "/test/controller/:id"),
                            equalTo(ERROR_TYPE, "_OTHER"))));
  }
}
