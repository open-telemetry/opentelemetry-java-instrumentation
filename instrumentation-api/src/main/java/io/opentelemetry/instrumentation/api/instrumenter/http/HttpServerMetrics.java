/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyActiveRequestsView;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyServerDurationView;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.instrumentation.api.instrumenter.RequestMetrics;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RequestListener} which keeps track of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#http-server">HTTP
 * server metrics</a>.
 */
@UnstableApi
public final class HttpServerMetrics implements RequestListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private static final ContextKey<State> HTTP_SERVER_REQUEST_METRICS_STATE =
      ContextKey.named("http-server-request-metrics-state");

  private static final Logger logger = LoggerFactory.getLogger(HttpServerMetrics.class);

  /**
   * Returns a {@link RequestMetrics} which can be used to enable recording of {@link
   * HttpServerMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  @UnstableApi
  public static RequestMetrics get() {
    return HttpServerMetrics::new;
  }

  private final LongUpDownCounter activeRequests;
  private final DoubleHistogram duration;
  private final Function<Context, String> httpRouteHolderGetter;

  private HttpServerMetrics(Meter meter) {
    this(meter, HttpRouteHolder::getRoute);
  }

  // visible for tests
  HttpServerMetrics(Meter meter, Function<Context, String> httpRouteHolderGetter) {
    activeRequests =
        meter
            .upDownCounterBuilder("http.server.active_requests")
            .setUnit("requests")
            .setDescription("The number of concurrent HTTP requests that are currently in-flight")
            .build();

    duration =
        meter
            .histogramBuilder("http.server.duration")
            .setUnit("ms")
            .setDescription("The duration of the inbound HTTP request")
            .build();

    this.httpRouteHolderGetter = httpRouteHolderGetter;
  }

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    activeRequests.add(1, applyActiveRequestsView(startAttributes), context);

    return context.with(
        HTTP_SERVER_REQUEST_METRICS_STATE,
        new AutoValue_HttpServerMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(HTTP_SERVER_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.debug(
          "No state present when ending context {}. Cannot reset HTTP request metrics.", context);
      return;
    }
    activeRequests.add(-1, applyActiveRequestsView(state.startAttributes()));
    duration.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_MS,
        applyServerDurationView(state.startAttributes(), addHttpRoute(context, endAttributes)),
        context);
  }

  // TODO: the http.route should be extracted by the AttributesExtractor, HttpServerMetrics should
  // not access the context to get it
  private Attributes addHttpRoute(Context context, Attributes endAttributes) {
    String route = httpRouteHolderGetter.apply(context);
    return route == null
        ? endAttributes
        : endAttributes.toBuilder().put(SemanticAttributes.HTTP_ROUTE, route).build();
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
