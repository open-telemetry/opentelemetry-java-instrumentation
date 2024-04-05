/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpMessageBodySizeUtil.getHttpRequestBodySize;
import static io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpMessageBodySizeUtil.getHttpResponseBodySize;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/metrics/semantic_conventions/http-metrics.md#http-client">non-stable
 * HTTP client metrics</a>: <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/metrics/semantic_conventions/http-metrics.md#metric-httpclientrequestsize">the
 * request size </a> and <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/metrics/semantic_conventions/http-metrics.md#metric-httpclientresponsesize">
 * the response size</a>.
 */
public final class HttpClientExperimentalMetrics implements OperationListener {
  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * HttpClientExperimentalMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create(
        "experimental http client", HttpClientExperimentalMetrics::new);
  }

  private final LongHistogram requestSize;
  private final LongHistogram responseSize;

  private HttpClientExperimentalMetrics(Meter meter) {
    LongHistogramBuilder requestSizeBuilder =
        meter
            .histogramBuilder("http.client.request.size")
            .setUnit("By")
            .setDescription("Size of HTTP client request bodies.")
            .ofLongs();
    HttpExperimentalMetricsAdvice.applyClientRequestSizeAdvice(requestSizeBuilder);
    requestSize = requestSizeBuilder.build();
    LongHistogramBuilder responseSizeBuilder =
        meter
            .histogramBuilder("http.client.response.size")
            .setUnit("By")
            .setDescription("Size of HTTP client response bodies.")
            .ofLongs();
    HttpExperimentalMetricsAdvice.applyClientRequestSizeAdvice(responseSizeBuilder);
    responseSize = responseSizeBuilder.build();
  }

  @Override
  @SuppressWarnings("OtelCanIgnoreReturnValueSuggester")
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context;
  }

  @Override
  public void onEnd(Context context, Attributes startAndEndAttributes, long startNanos, long endNanos) {
    Long requestBodySize = getHttpRequestBodySize(startAndEndAttributes);
    if (requestBodySize != null) {
      requestSize.record(requestBodySize, startAndEndAttributes, context);
    }

    Long responseBodySize = getHttpResponseBodySize(startAndEndAttributes);
    if (responseBodySize != null) {
      responseSize.record(responseBodySize, startAndEndAttributes, context);
    }
  }
}
