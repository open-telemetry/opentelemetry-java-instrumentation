/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleValueRecorder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.metrics.common.LabelsBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.instrumentation.api.instrumenter.RequestMetrics;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RequestListener} which keeps track of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#http-server">HTTP
 * server metrics</a>.
 *
 * <p>To use this class, you may need to add the {@code opentelemetry-api-metrics} artifact to your
 * dependencies.
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
  private final DoubleValueRecorder duration;

  private HttpServerMetrics(Meter meter) {
    activeRequests =
        meter
            .longUpDownCounterBuilder("http.server.active_requests")
            .setUnit("requests")
            .setDescription("The number of concurrent HTTP requests that are currently in-flight")
            .build();

    duration =
        meter
            .doubleValueRecorderBuilder("http.server.duration")
            .setUnit("milliseconds")
            .setDescription("The duration of the inbound HTTP request")
            .build();
  }

  @Override
  public Context start(Context context, Attributes requestAttributes) {
    long startTimeNanos = System.nanoTime();
    Labels activeRequestLabels = activeRequestLabels(requestAttributes);
    Labels durationLabels = durationLabels(requestAttributes);
    activeRequests.add(1, activeRequestLabels);

    return context.with(
        HTTP_SERVER_REQUEST_METRICS_STATE,
        new AutoValue_HttpServerMetrics_State(activeRequestLabels, durationLabels, startTimeNanos));
  }

  @Override
  public void end(Context context, Attributes responseAttributes) {
    State state = context.get(HTTP_SERVER_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.debug(
          "No state present when ending context {}. Cannot reset HTTP request metrics.", context);
      return;
    }
    activeRequests.add(-1, state.activeRequestLabels());
    duration.record(
        (System.nanoTime() - state.startTimeNanos()) / NANOS_PER_MS, state.durationLabels());
  }

  private static Labels activeRequestLabels(Attributes attributes) {
    LabelsBuilder labels = Labels.builder();
    attributes.forEach(
        (key, value) -> {
          if (key.getType() != AttributeType.STRING) {
            return;
          }
          switch (key.getKey()) {
            case "http.method":
            case "http.host":
            case "http.scheme":
            case "http.flavor":
            case "http.server_name":
              labels.put(key.getKey(), (String) value);
              break;
            default:
              // fall through
          }
        });
    return labels.build();
  }

  private static Labels durationLabels(Attributes attributes) {
    LabelsBuilder labels = Labels.builder();
    attributes.forEach(
        (key, value) -> {
          switch (key.getKey()) {
            case "http.method":
            case "http.host":
            case "http.scheme":
            case "http.flavor":
            case "http.server_name":
            case "net.host.name":
              if (value instanceof String) {
                labels.put(key.getKey(), (String) value);
              }
              break;
            case "http.status_code":
            case "net.host.port":
              if (value instanceof Long) {
                labels.put(key.getKey(), Long.toString((long) value));
              }
              break;
            default:
              // fall through
          }
        });
    return labels.build();
  }

  @AutoValue
  abstract static class State {

    abstract Labels activeRequestLabels();

    abstract Labels durationLabels();

    abstract long startTimeNanos();
  }
}
