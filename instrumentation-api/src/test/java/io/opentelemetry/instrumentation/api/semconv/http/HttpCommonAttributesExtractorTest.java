/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorTest.TestHttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorTest.TestHttpServerAttributesGetter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HttpCommonAttributesExtractorTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @ParameterizedTest
  @CsvSource({
    "true, 200, true, , java.io.IOException, ERROR",
    "false, 200, true, , java.io.IOException, ERROR",
    "true, 302, true, , java.io.IOException, ERROR",
    "false, 302, true, , java.io.IOException, ERROR",
    "true, 404, true, , java.io.IOException, ERROR",
    "false, 404, true, , 404, ERROR",
    "true, 200, true, body_failure, body_failure, ERROR",
    "false, 200, true, body_failure, body_failure, ERROR",
    "true, 500, true, body_failure, 500, ERROR",
    "false, 500, true, body_failure, 500, ERROR",
    "true, 200, false, , , UNSET",
    "false, 200, false, , , UNSET",
    "true, 302, false, , , UNSET",
    "false, 302, false, , , UNSET",
    "true, 404, false, , , UNSET",
    "false, 404, false, , 404, ERROR",
    "true, 500, false, , 500, ERROR",
    "false, 500, false, , 500, ERROR",
    "true, , true, , java.io.IOException, ERROR",
    "false, , true, , java.io.IOException, ERROR",
    "true, 0, true, , java.io.IOException, ERROR",
    "false, 0, true, , java.io.IOException, ERROR",
    "true, , false, , _OTHER, UNSET",
    "false, , false, , _OTHER, UNSET",
    "false, , false, cancelled, cancelled, UNSET",
    "false, 200, false, cancelled, , UNSET"
  })
  void classifiesSpanAndDuration(
      boolean server,
      Integer statusCode,
      boolean terminalFailure,
      String customErrorType,
      String expectedErrorType,
      StatusCode expectedStatus) {
    InstrumenterBuilder<Map<String, String>, Map<String, String>> builder =
        Instrumenter.builder(testing.getOpenTelemetry(), "test", request -> "HTTP");
    if (server) {
      TestHttpServerAttributesGetter getter = new TestHttpServerAttributesGetter();
      builder
          .addAttributesExtractor(HttpServerAttributesExtractor.create(getter))
          .setSpanStatusExtractor(HttpSpanStatusExtractor.create(getter))
          .addOperationMetrics(HttpServerMetrics.get());
    } else {
      TestHttpClientAttributesGetter getter = new TestHttpClientAttributesGetter();
      builder
          .addAttributesExtractor(HttpClientAttributesExtractor.create(getter))
          .setSpanStatusExtractor(HttpSpanStatusExtractor.create(getter))
          .addOperationMetrics(HttpClientMetrics.get());
    }
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        builder.buildInstrumenter(request -> server ? SpanKind.SERVER : SpanKind.CLIENT);

    Map<String, String> request = new HashMap<>();
    request.put("method", "GET");
    request.put("errorType", customErrorType);
    Map<String, String> response = null;
    if (statusCode != null) {
      response = new HashMap<>();
      response.put("statusCode", statusCode.toString());
    }

    Context context = instrumenter.start(Context.root(), request);
    instrumenter.end(
        context, request, response, terminalFailure ? new IOException("body failed") : null);

    Long expectedStatusCode = statusCode != null && statusCode > 0 ? statusCode.longValue() : null;
    assertThat(testing.getSpans())
        .satisfiesExactly(
            span ->
                assertThat(span)
                    .hasKind(server ? SpanKind.SERVER : SpanKind.CLIENT)
                    .hasStatus(StatusData.create(expectedStatus, ""))
                    .hasAttributesSatisfyingExactly(
                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                        equalTo(HTTP_RESPONSE_STATUS_CODE, expectedStatusCode),
                        equalTo(ERROR_TYPE, expectedErrorType)));
    assertThat(testing.getMetrics())
        .satisfiesExactly(
            metric ->
                assertThat(metric)
                    .hasName(
                        server ? "http.server.request.duration" : "http.client.request.duration")
                    .hasUnit("s")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                                            equalTo(HTTP_RESPONSE_STATUS_CODE, expectedStatusCode),
                                            equalTo(ERROR_TYPE, expectedErrorType)))));
  }
}
