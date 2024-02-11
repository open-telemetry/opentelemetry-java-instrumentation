/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.metrics;

import static io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.metrics.AerospikeMessageSizeUtil.getMessageSize;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class AerospikeMetrics implements OperationListener {
  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private static final ContextKey<State> AEROSPIKE_CLIENT_METRICS_STATE =
      ContextKey.named("aerospike-client-metrics-state");

  private static final Logger logger = Logger.getLogger(AerospikeMetrics.class.getName());

  private final LongCounter requestCounter;

  private final LongCounter responseCounter;

  private final LongUpDownCounter concurrencyUpDownCounter;

  private final DoubleHistogram clientLatencyHistogram;

  private final DoubleHistogram recordSizeHistogram;

  private AerospikeMetrics(Meter meter) {
    LongCounterBuilder requestCounterBuilder =
        meter.counterBuilder("aerospike.requests").setDescription("Aerospike Calls");
    AerospikeMetricsAdvice.applyRequestCounterAdvice(requestCounterBuilder);
    requestCounter = requestCounterBuilder.build();

    LongCounterBuilder responseCounterBuilder =
        meter.counterBuilder("aerospike.response").setDescription("Aerospike Responses");
    AerospikeMetricsAdvice.applyResponseCounterAdvice(responseCounterBuilder);
    responseCounter = responseCounterBuilder.build();

    LongUpDownCounterBuilder concurrencyUpDownCounterBuilder =
        meter
            .upDownCounterBuilder("aerospike.concurrency")
            .setDescription("Aerospike Concurrent Requests");
    AerospikeMetricsAdvice.applyConcurrencyUpDownCounterAdvice(concurrencyUpDownCounterBuilder);
    concurrencyUpDownCounter = concurrencyUpDownCounterBuilder.build();

    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder("aerospike.client.duration")
            .setDescription("Aerospike Response Latency")
            .setUnit("ms");
    AerospikeMetricsAdvice.applyClientDurationAdvice(durationBuilder);
    clientLatencyHistogram = durationBuilder.build();

    DoubleHistogramBuilder recordSizeHistogramBuilder =
        meter
            .histogramBuilder("aerospike.record.size")
            .setDescription("Aerospike Record Size")
            .setUnit("By");
    AerospikeMetricsAdvice.applyRecordSizeAdvice(recordSizeHistogramBuilder);
    recordSizeHistogram = recordSizeHistogramBuilder.build();
  }

  public static OperationMetrics get() {
    return AerospikeMetrics::new;
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    requestCounter.add(1, startAttributes, context);
    concurrencyUpDownCounter.add(1, startAttributes, context);
    return context.with(
        AEROSPIKE_CLIENT_METRICS_STATE,
        new AutoValue_AerospikeMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(AEROSPIKE_CLIENT_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record Aerospike End Call metrics.",
          context);
      return;
    }
    concurrencyUpDownCounter.add(-1, state.startAttributes(), context);
    Attributes mergedAttributes = state.startAttributes().toBuilder().putAll(endAttributes).build();
    responseCounter.add(1, mergedAttributes, context);
    clientLatencyHistogram.record(
        (endNanos - state.startTimeNanos()) / NANOS_PER_MS, mergedAttributes, context);
    Long requestBodySize = getMessageSize(mergedAttributes);
    if (requestBodySize != null) {
      recordSizeHistogram.record(requestBodySize, mergedAttributes, context);
    }
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}
