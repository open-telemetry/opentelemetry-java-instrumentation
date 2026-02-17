/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class DropwizardMetricsAdapter implements MetricRegistryListener {

  private static final Logger logger = Logger.getLogger(DropwizardMetricsAdapter.class.getName());

  private static final double NANOS_PER_MS = MILLISECONDS.toNanos(1);
  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^a-zA-Z0-9._/-]");

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

  /**
   * Sanitizes instrument names to comply with OpenTelemetry specification. Instrument names must
   * consist of alphanumeric characters, '_', '.', '-', '/', and must start with a letter. Invalid
   * characters are stripped from the name. Logs a warning if the name is changed or if the name is
   * invalid and cannot be sanitized.
   *
   * @param name the original metric name from Dropwizard
   * @return the sanitized instrument name, or null if invalid (causing instrument creation to be
   *     skipped)
   */
  private static String sanitizeInstrumentName(String name) {
    if (name == null || name.isEmpty()) {
      logger.log(
          Level.WARNING, "Dropwizard metric name is null or empty, skipping instrument creation");
      return null;
    }

    // Strip all characters that are not alphanumeric, '_', '.', '-', or '/'
    String sanitized = INVALID_CHARACTERS.matcher(name).replaceAll("");

    if (sanitized.isEmpty()) {
      logger.log(
          Level.WARNING,
          "Dropwizard metric name ''{0}'' contains no valid characters after sanitization, skipping instrument creation",
          name);
      return null;
    }

    // Ensure the name starts with a letter
    if (!Character.isLetter(sanitized.charAt(0))) {
      logger.log(
          Level.WARNING,
          "Dropwizard metric name ''{0}'' does not start with a letter after sanitization, skipping instrument creation",
          name);
      return null;
    }

    // Ensure max length of 255 characters (OpenTelemetry specification limit)
    if (sanitized.length() > 255) {
      logger.log(
          Level.WARNING,
          "Dropwizard metric name ''{0}'' exceeds 255 character limit, truncating to ''{1}''",
          new Object[] {name, sanitized.substring(0, 255)});
      sanitized = sanitized.substring(0, 255);
    }

    // Log if sanitization changed the name
    if (!sanitized.equals(name)) {
      logger.log(
          Level.WARNING,
          "Dropwizard metric name ''{0}'' has been sanitized to ''{1}''",
          new Object[] {name, sanitized});
    }

    return sanitized;
  }

  @Override
  public void onGaugeAdded(String name, Gauge<?> gauge) {
    String sanitizedName = sanitizeInstrumentName(name);
    if (sanitizedName == null) {
      return;
    }
    ObservableDoubleGauge otelGauge =
        otelMeter
            .gaugeBuilder(sanitizedName)
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
    String sanitizedName = sanitizeInstrumentName(name);
    if (sanitizedName == null) {
      return;
    }
    dropwizardCounters.put(name, dropwizardCounter);
    LongUpDownCounter otelCounter =
        otelUpDownCounters.computeIfAbsent(
            name, n -> otelMeter.upDownCounterBuilder(sanitizedName).build());
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
    String sanitizedName = sanitizeInstrumentName(name);
    if (sanitizedName == null) {
      return;
    }
    dropwizardHistograms.put(name, dropwizardHistogram);
    LongHistogram otelHistogram =
        otelHistograms.computeIfAbsent(
            name, n -> otelMeter.histogramBuilder(sanitizedName).ofLongs().build());
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
    String sanitizedName = sanitizeInstrumentName(name);
    if (sanitizedName == null) {
      return;
    }
    dropwizardMeters.put(name, dropwizardMeter);
    LongCounter otelCounter =
        otelCounters.computeIfAbsent(name, n -> otelMeter.counterBuilder(sanitizedName).build());
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
    String sanitizedName = sanitizeInstrumentName(name);
    if (sanitizedName == null) {
      return;
    }
    dropwizardTimers.put(name, dropwizardTimer);
    DoubleHistogram otelHistogram =
        otelDoubleHistograms.computeIfAbsent(
            name, n -> otelMeter.histogramBuilder(sanitizedName).setUnit("ms").build());
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
