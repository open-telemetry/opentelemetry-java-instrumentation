/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpMessageBodySizeUtil.getHttpRequestBodySize;
import static io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpMessageBodySizeUtil.getHttpResponseBodySize;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#http-client">non-stable
 * HTTP client metrics</a>: <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpclientrequestbodysize">the
 * request size </a> and <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpclientresponsebodysize">
 * the response size</a>.
 */
public final class HttpClientExperimentalMetrics implements OperationListener {

  private static final ContextKey<Attributes> HTTP_CLIENT_REQUEST_METRICS_START_ATTRIBUTES =
      ContextKey.named("http-client-experimental-metrics-start-attributes");

  private static final Logger logger =
      Logger.getLogger(HttpClientExperimentalMetrics.class.getName());

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
            .histogramBuilder("http.client.request.body.size")
            .setUnit("By")
            .setDescription("Size of HTTP client request bodies.")
            .ofLongs();
    HttpExperimentalMetricsAdvice.applyClientRequestSizeAdvice(requestSizeBuilder);
    requestSize = requestSizeBuilder.build();
    LongHistogramBuilder responseSizeBuilder =
        meter
            .histogramBuilder("http.client.response.body.size")
            .setUnit("By")
            .setDescription("Size of HTTP client response bodies.")
            .ofLongs();
    HttpExperimentalMetricsAdvice.applyClientRequestSizeAdvice(responseSizeBuilder);
    responseSize = responseSizeBuilder.build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(HTTP_CLIENT_REQUEST_METRICS_START_ATTRIBUTES, startAttributes);
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    Attributes startAttributes = context.get(HTTP_CLIENT_REQUEST_METRICS_START_ATTRIBUTES);
    if (startAttributes == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record HTTP request metrics.",
          context);
      return;
    }

    Attributes sizeAttributes = startAttributes.toBuilder().putAll(endAttributes).build();

    Long requestBodySize = getHttpRequestBodySize(endAttributes, startAttributes);
    if (requestBodySize != null) {
      requestSize.record(requestBodySize, sizeAttributes, context);
    }

    Long responseBodySize = getHttpResponseBodySize(endAttributes, startAttributes);
    if (responseBodySize != null) {
      responseSize.record(responseBodySize, sizeAttributes, context);
    }
  }
}
