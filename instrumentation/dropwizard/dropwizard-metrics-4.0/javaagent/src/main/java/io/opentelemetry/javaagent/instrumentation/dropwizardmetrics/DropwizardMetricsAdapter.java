/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DropwizardMetricsAdapter implements MetricRegistryListener {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private static final VirtualField<Counter, LongUpDownCounter> otelUpDownCounterField =
      VirtualField.find(Counter.class, LongUpDownCounter.class);
  private static final VirtualField<Histogram, LongHistogram> otelHistogramField =
      VirtualField.find(Histogram.class, LongHistogram.class);
  private static final VirtualField<Meter, LongCounter> otelCounterField =
      VirtualField.find(Meter.class, LongCounter.class);
  private static final VirtualField<Timer, DoubleHistogram> otelDoubleHistogramField =
      VirtualField.find(Timer.class, DoubleHistogram.class);

  private final io.opentelemetry.api.metrics.Meter otelMeter;

  private final Map<String, DoubleHistogram> otelDoubleHistograms = new ConcurrentHashMap<>();
  private final Map<String, LongCounter> otelCounters = new ConcurrentHashMap<>();
  private final Map<String, LongHistogram> otelHistograms = new ConcurrentHashMap<>();
  private final Map<String, LongUpDownCounter> otelUpDownCounters = new ConcurrentHashMap<>();
  private final Map<String, ObservableDoubleGauge> otelGauges = new ConcurrentHashMap<>();

  private final Map<String, Counter> dropwizardCounters = new ConcurrentHashMap<>();
  private final Map<String, Histogram> dropwizardHistograms = new ConcurrentHashMap<>();
  private final Map<String, Meter> dropwizardMeters = new ConcurrentHashMap<>();
  private final Map<String, Timer> dropwizardTimers = new ConcurrentHashMap<>();

  public DropwizardMetricsAdapter(OpenTelemetry openTelemetry) {
    this.otelMeter = openTelemetry.getMeter("io.opentelemetry.dropwizard-metrics-4.0");
  }

  @Override
  public void onGaugeAdded(String name, Gauge<?> gauge) {
    ObservableDoubleGauge otelGauge =
        otelMeter
            .gaugeBuilder(name)
            .buildWithCallback(
                measurement -> {
                  Object val = gauge.getValue();
                  if (val instanceof Number) {
                    measurement.record(((Number) val).doubleValue());
                  }
                });
    otelGauges.put(name, otelGauge);
  }

  @Override
  public void onGaugeRemoved(String name) {
    ObservableDoubleGauge otelGauge = otelGauges.remove(name);
    if (otelGauge != null) {
      otelGauge.close();
    }
  }

  @Override
  public void onCounterAdded(String name, Counter dropwizardCounter) {
    dropwizardCounters.put(name, dropwizardCounter);
    LongUpDownCounter otelCounter =
        otelUpDownCounters.computeIfAbsent(name, n -> otelMeter.upDownCounterBuilder(n).build());
    otelUpDownCounterField.set(dropwizardCounter, otelCounter);
  }

  @Override
  public void onCounterRemoved(String name) {
    Counter dropwizardCounter = dropwizardCounters.remove(name);
    otelUpDownCounters.remove(name);
    if (dropwizardCounter != null) {
      otelUpDownCounterField.set(dropwizardCounter, null);
    }
  }

  public void counterAdd(Counter dropwizardCounter, long increment) {
    LongUpDownCounter otelCounter = otelUpDownCounterField.get(dropwizardCounter);
    if (otelCounter != null) {
      otelCounter.add(increment);
    }
  }

  @Override
  public void onHistogramAdded(String name, Histogram dropwizardHistogram) {
    dropwizardHistograms.put(name, dropwizardHistogram);
    LongHistogram otelHistogram =
        otelHistograms.computeIfAbsent(name, n -> otelMeter.histogramBuilder(n).ofLongs().build());
    otelHistogramField.set(dropwizardHistogram, otelHistogram);
  }

  @Override
  public void onHistogramRemoved(String name) {
    Histogram dropwizardHistogram = dropwizardHistograms.remove(name);
    otelHistograms.remove(name);
    if (dropwizardHistogram != null) {
      otelHistogramField.set(dropwizardHistogram, null);
    }
  }

  public void histogramUpdate(Histogram dropwizardHistogram, long value) {
    LongHistogram otelHistogram = otelHistogramField.get(dropwizardHistogram);
    if (otelHistogram != null) {
      otelHistogram.record(value);
    }
  }

  @Override
  public void onMeterAdded(String name, Meter dropwizardMeter) {
    dropwizardMeters.put(name, dropwizardMeter);
    LongCounter otelCounter =
        otelCounters.computeIfAbsent(name, n -> otelMeter.counterBuilder(n).build());
    otelCounterField.set(dropwizardMeter, otelCounter);
  }

  @Override
  public void onMeterRemoved(String name) {
    Meter dropwizardMeter = dropwizardMeters.remove(name);
    otelCounters.remove(name);
    if (dropwizardMeter != null) {
      otelCounterField.set(dropwizardMeter, null);
    }
  }

  public void meterMark(Meter dropwizardMeter, long increment) {
    LongCounter otelCounter = otelCounterField.get(dropwizardMeter);
    if (otelCounter != null) {
      otelCounter.add(increment);
    }
  }

  @Override
  public void onTimerAdded(String name, Timer dropwizardTimer) {
    dropwizardTimers.put(name, dropwizardTimer);
    DoubleHistogram otelHistogram =
        otelDoubleHistograms.computeIfAbsent(
            name, n -> otelMeter.histogramBuilder(n).setUnit("ms").build());
    otelDoubleHistogramField.set(dropwizardTimer, otelHistogram);
  }

  @Override
  public void onTimerRemoved(String name) {
    Timer dropwizardTimer = dropwizardTimers.remove(name);
    otelDoubleHistograms.remove(name);
    if (dropwizardTimer != null) {
      otelDoubleHistogramField.set(dropwizardTimer, null);
    }
  }

  public void timerUpdate(Timer dropwizardTimer, long nanos) {
    DoubleHistogram otelHistogram = otelDoubleHistogramField.get(dropwizardTimer);
    if (otelHistogram != null) {
      otelHistogram.record(nanos / NANOS_PER_MS);
    }
  }
}
