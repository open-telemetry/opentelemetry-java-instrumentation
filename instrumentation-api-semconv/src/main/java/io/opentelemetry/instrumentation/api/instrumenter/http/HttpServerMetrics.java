/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpMetricsUtil.createStableDurationHistogramBuilder;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/metrics/semantic_conventions/http-metrics.md#http-server">HTTP
 * server metrics</a>.
 */
public final class HttpServerMetrics implements OperationListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  private static final ContextKey<State> HTTP_SERVER_METRICS_STATE =
      ContextKey.named("http-server-metrics-state");

  private static final Logger logger = Logger.getLogger(HttpServerMetrics.class.getName());

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * HttpServerMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return HttpServerMetrics::new;
  }

  @Nullable private final DoubleHistogram stableDuration;
  @Nullable private final DoubleHistogram oldDuration;

  private HttpServerMetrics(Meter meter) {
    if (SemconvStability.emitStableHttpSemconv()) {
      DoubleHistogramBuilder stableDurationBuilder =
          createStableDurationHistogramBuilder(
              meter, "http.server.request.duration", "Duration of HTTP server requests.");
      HttpMetricsAdvice.applyStableServerDurationAdvice(stableDurationBuilder);
      stableDuration = stableDurationBuilder.build();
    } else {
      stableDuration = null;
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      DoubleHistogramBuilder oldDurationBuilder =
          meter
              .histogramBuilder("http.server.duration")
              .setUnit("ms")
              .setDescription("The duration of the inbound HTTP request");
      HttpMetricsAdvice.applyOldServerDurationAdvice(oldDurationBuilder);
      oldDuration = oldDurationBuilder.build();
    } else {
      oldDuration = null;
    }
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        HTTP_SERVER_METRICS_STATE,
        new AutoValue_HttpServerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(HTTP_SERVER_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record HTTP request metrics.",
          context);
      return;
    }

    Attributes attributes = state.startAttributes().toBuilder().putAll(endAttributes).build();

    if (stableDuration != null) {
      stableDuration.record((endNanos - state.startTimeNanos()) / NANOS_PER_S, attributes, context);
    }

    if (oldDuration != null) {
      oldDuration.record((endNanos - state.startTimeNanos()) / NANOS_PER_MS, attributes, context);
    }
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
