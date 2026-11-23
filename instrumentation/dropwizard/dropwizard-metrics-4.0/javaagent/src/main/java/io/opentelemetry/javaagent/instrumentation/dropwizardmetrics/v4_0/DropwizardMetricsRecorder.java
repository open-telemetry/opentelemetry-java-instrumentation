/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics.v4_0;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class DropwizardMetricsRecorder {

  private static final double NANOS_PER_MS = MILLISECONDS.toNanos(1);

  private static final VirtualField<Counter, LongUpDownCounter> OTEL_LONG_UP_DOWN_COUNTER =
      VirtualField.find(Counter.class, LongUpDownCounter.class);
  private static final VirtualField<Histogram, LongHistogram> OTEL_LONG_HISTOGRAM =
      VirtualField.find(Histogram.class, LongHistogram.class);
  private static final VirtualField<Meter, LongCounter> OTEL_LONG_COUNTER =
      VirtualField.find(Meter.class, LongCounter.class);
  private static final VirtualField<Timer, DoubleHistogram> OTEL_DOUBLE_HISTOGRAM =
      VirtualField.find(Timer.class, DoubleHistogram.class);

  static void bindCounter(Counter dropwizardCounter, LongUpDownCounter otelCounter) {
    OTEL_LONG_UP_DOWN_COUNTER.set(dropwizardCounter, otelCounter);
  }

  static void unbindCounter(Counter dropwizardCounter) {
    OTEL_LONG_UP_DOWN_COUNTER.set(dropwizardCounter, null);
  }

  public static void counterAdd(Counter dropwizardCounter, long increment) {
    LongUpDownCounter otelCounter = OTEL_LONG_UP_DOWN_COUNTER.get(dropwizardCounter);
    if (otelCounter != null) {
      otelCounter.add(increment);
    }
  }

  static void bindHistogram(Histogram dropwizardHistogram, LongHistogram otelHistogram) {
    OTEL_LONG_HISTOGRAM.set(dropwizardHistogram, otelHistogram);
  }

  static void unbindHistogram(Histogram dropwizardHistogram) {
    OTEL_LONG_HISTOGRAM.set(dropwizardHistogram, null);
  }

  public static void histogramUpdate(Histogram dropwizardHistogram, long value) {
    LongHistogram otelHistogram = OTEL_LONG_HISTOGRAM.get(dropwizardHistogram);
    if (otelHistogram != null) {
      otelHistogram.record(value);
    }
  }

  static void bindMeter(Meter dropwizardMeter, LongCounter otelCounter) {
    OTEL_LONG_COUNTER.set(dropwizardMeter, otelCounter);
  }

  static void unbindMeter(Meter dropwizardMeter) {
    OTEL_LONG_COUNTER.set(dropwizardMeter, null);
  }

  public static void meterMark(Meter dropwizardMeter, long increment) {
    LongCounter otelCounter = OTEL_LONG_COUNTER.get(dropwizardMeter);
    if (otelCounter != null) {
      otelCounter.add(increment);
    }
  }

  static void bindTimer(Timer dropwizardTimer, DoubleHistogram otelHistogram) {
    OTEL_DOUBLE_HISTOGRAM.set(dropwizardTimer, otelHistogram);
  }

  static void unbindTimer(Timer dropwizardTimer) {
    OTEL_DOUBLE_HISTOGRAM.set(dropwizardTimer, null);
  }

  public static void timerUpdate(Timer dropwizardTimer, long nanos) {
    DoubleHistogram otelHistogram = OTEL_DOUBLE_HISTOGRAM.get(dropwizardTimer);
    if (otelHistogram != null) {
      otelHistogram.record(nanos / NANOS_PER_MS);
    }
  }

  private DropwizardMetricsRecorder() {}
}
