/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyActiveRequestsView;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#http-server">HTTP
 * server metrics</a>.
 */
public final class HttpServerMetrics implements OperationListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private static final ContextKey<State> HTTP_SERVER_REQUEST_METRICS_STATE =
      ContextKey.named("http-server-request-metrics-state");

  private static final Logger logger = Logger.getLogger(HttpServerMetrics.class.getName());

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * HttpServerMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return HttpServerMetrics::new;
  }

  private final LongUpDownCounter activeRequests;
  private final DoubleHistogram duration;
  private final LongHistogram requestSize;
  private final LongHistogram responseSize;

  private HttpServerMetrics(Meter meter) {
    activeRequests =
        meter
            .upDownCounterBuilder("http.server.active_requests")
            .setUnit("{requests}")
            .setDescription("The number of concurrent HTTP requests that are currently in-flight")
            .build();

    duration =
        meter
            .histogramBuilder("http.server.duration")
            .setUnit("ms")
            .setDescription("The duration of the inbound HTTP request")
            .build();
    requestSize =
        meter
            .histogramBuilder("http.server.request.size")
            .setUnit("By")
            .setDescription("The size of HTTP request messages")
            .ofLongs()
            .build();
    responseSize =
        meter
            .histogramBuilder("http.server.response.size")
            .setUnit("By")
            .setDescription("The size of HTTP response messages")
            .ofLongs()
            .build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    activeRequests.add(1, applyActiveRequestsView(startAttributes), context);

    return context.with(
        HTTP_SERVER_REQUEST_METRICS_STATE,
        new AutoValue_HttpServerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(HTTP_SERVER_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record HTTP request metrics.",
          context);
      return;
    }
    Attributes attributes = mergeAttributes(state.startAttributes(), endAttributes);
    activeRequests.add(-1, attributes, context);
    duration.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_MS, attributes, context);
    Long requestLength = attributes.get(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH);
    if (requestLength != null) {
      requestSize.record(requestLength, attributes);
    }
    Long responseLength = attributes.get(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH);
    if (responseLength != null) {
      responseSize.record(responseLength, attributes);
    }
  }

  private static Attributes mergeAttributes(Attributes attr1, Attributes attr2) {
    return Attributes.builder().putAll(attr1).putAll(attr2).build();
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
