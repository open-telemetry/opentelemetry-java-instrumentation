/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.function.Consumer;

/**
 * Camel http producer spans do not use the http semantic attributes extractor, so they do not claim
 * the http client span key. Under the {@code semconv} span suppression strategy the http client
 * instrumentation nested inside a Camel http producer is therefore not suppressed and emits its own
 * span, while under the {@code span-kind} strategy the Camel client span suppresses it.
 */
class SuppressionTest {

  private static final boolean NESTED_HTTP_CLIENT_SPAN =
      "semconv"
          .equals(
              System.getProperty("otel.instrumentation.experimental.span-suppression-strategy"));

  /** Returns the number of spans that the nested http client instrumentation adds to a trace. */
  static int nestedHttpClientSpans() {
    return NESTED_HTTP_CLIENT_SPAN ? 1 : 0;
  }

  static void addNestedHttpClientSpan(
      List<Consumer<SpanDataAssert>> assertions,
      String method,
      String url,
      String serverAddress,
      long serverPort,
      SpanData parent) {
    if (!NESTED_HTTP_CLIENT_SPAN) {
      return;
    }
    assertions.add(
        span ->
            span.hasName(method)
                .hasKind(SpanKind.CLIENT)
                .hasParent(parent)
                .hasAttributesSatisfyingExactly(
                    equalTo(HTTP_REQUEST_METHOD, method),
                    equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                    equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                    equalTo(maybeStablePeerService(), "test-peer-service"),
                    equalTo(SERVER_ADDRESS, serverAddress),
                    equalTo(SERVER_PORT, serverPort),
                    equalTo(URL_FULL, url)));
  }

  private SuppressionTest() {}
}
