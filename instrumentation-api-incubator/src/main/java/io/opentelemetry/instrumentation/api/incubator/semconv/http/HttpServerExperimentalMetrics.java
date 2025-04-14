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
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#http-server">non-stable
 * HTTP server metrics</a>: <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserveractive_requests">the
 * number of in-flight request</a>, <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverrequestbodysize">the
 * request size</a> and <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverresponsebodysize">the
 * response size</a>.
 */
public final class HttpServerExperimentalMetrics implements OperationListener {

  private static final ContextKey<Attributes> HTTP_SERVER_EXPERIMENTAL_METRICS_START_ATTRIBUTES =
      ContextKey.named("http-server-experimental-metrics-start-attributes");

  private static final Logger logger =
      Logger.getLogger(HttpServerExperimentalMetrics.class.getName());

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * HttpServerExperimentalMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return OperationMetricsUtil.create(
        "experimental http server", HttpServerExperimentalMetrics::new);
  }

  private final LongUpDownCounter activeRequests;
  private final LongHistogram requestSize;
  private final LongHistogram responseSize;

  private HttpServerExperimentalMetrics(Meter meter) {
    LongUpDownCounterBuilder activeRequestsBuilder =
        meter
            .upDownCounterBuilder("http.server.active_requests")
            .setUnit("{requests}")
            .setDescription("Number of active HTTP server requests.");
    HttpExperimentalMetricsAdvice.applyServerActiveRequestsAdvice(activeRequestsBuilder);
    activeRequests = activeRequestsBuilder.build();
    LongHistogramBuilder requestSizeBuilder =
        meter
            .histogramBuilder("http.server.request.body.size")
            .setUnit("By")
            .setDescription("Size of HTTP server request bodies.")
            .ofLongs();
    HttpExperimentalMetricsAdvice.applyServerRequestSizeAdvice(requestSizeBuilder);
    requestSize = requestSizeBuilder.build();
    LongHistogramBuilder responseSizeBuilder =
        meter
            .histogramBuilder("http.server.response.body.size")
            .setUnit("By")
            .setDescription("Size of HTTP server response bodies.")
            .ofLongs();
    HttpExperimentalMetricsAdvice.applyServerRequestSizeAdvice(responseSizeBuilder);
    responseSize = responseSizeBuilder.build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    activeRequests.add(1, startAttributes, context);

    return context.with(HTTP_SERVER_EXPERIMENTAL_METRICS_START_ATTRIBUTES, startAttributes);
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    Attributes startAttributes = context.get(HTTP_SERVER_EXPERIMENTAL_METRICS_START_ATTRIBUTES);
    if (startAttributes == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record HTTP request metrics.",
          context);
      return;
    }
    // it's important to use exactly the same attributes that were used when incrementing the active
    // request count (otherwise it will split the timeseries)
    activeRequests.add(-1, startAttributes, context);

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
