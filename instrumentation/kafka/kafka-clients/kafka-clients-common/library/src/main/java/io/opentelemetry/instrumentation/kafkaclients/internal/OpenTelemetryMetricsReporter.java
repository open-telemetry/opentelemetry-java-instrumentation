/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import io.opentelemetry.instrumentation.kafkaclients.OpenTelemetryKafkaMetrics;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;

/**
 * A {@link MetricsReporter} which bridges Kafka metrics to OpenTelemetry metrics.
 *
 * <p>To configure, use {@link OpenTelemetryKafkaMetrics#getConfigProperties(OpenTelemetry)}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class OpenTelemetryMetricsReporter implements MetricsReporter {

  public static final String CONFIG_KEY_OPENTELEMETRY_INSTANCE = "opentelemetry.instance";

  private static final Logger logger =
      Logger.getLogger(OpenTelemetryMetricsReporter.class.getName());
  private volatile Meter meter;

  private static final Object lock = new Object();

  @GuardedBy("lock")
  private static final List<RegisteredObservable> registeredObservables = new ArrayList<>();

  /**
   * Reset for test by reseting the {@link #meter} to {@code null} and closing all registered
   * instruments.
   */
  static void resetForTest() {
    closeAllInstruments();
  }

  // Visible for test
  static List<RegisteredObservable> getRegisteredObservables() {
    synchronized (lock) {
      return new ArrayList<>(registeredObservables);
    }
  }

  @Override
  public void init(List<KafkaMetric> metrics) {
    metrics.forEach(this::metricChange);
  }

  @Override
  public void metricChange(KafkaMetric metric) {
    Meter currentMeter = meter;
    if (currentMeter == null) {
      // Ignore if meter hasn't been initialized in configure(Map<String, ?)
      return;
    }

    RegisteredObservable registeredObservable =
        KafkaMetricRegistry.getRegisteredObservable(currentMeter, metric);
    if (registeredObservable == null) {
      logger.log(
          Level.FINEST, "Metric changed but cannot map to instrument: {0}", metric.metricName());
      return;
    }

    Set<AttributeKey<?>> attributeKeys = registeredObservable.getAttributes().asMap().keySet();
    synchronized (lock) {
      for (Iterator<RegisteredObservable> it = registeredObservables.iterator(); it.hasNext(); ) {
        RegisteredObservable curRegisteredObservable = it.next();
        Set<AttributeKey<?>> curAttributeKeys =
            curRegisteredObservable.getAttributes().asMap().keySet();
        if (curRegisteredObservable.getKafkaMetricName().equals(metric.metricName())) {
          logger.log(Level.FINEST, "Replacing instrument: {0}", curRegisteredObservable);
          closeInstrument(curRegisteredObservable.getObservable());
          it.remove();
        } else if (curRegisteredObservable
                .getInstrumentDescriptor()
                .equals(registeredObservable.getInstrumentDescriptor())
            && attributeKeys.size() > curAttributeKeys.size()
            && attributeKeys.containsAll(curAttributeKeys)) {
          logger.log(
              Level.FINEST,
              "Replacing instrument with higher dimension version: {0}",
              curRegisteredObservable);
          closeInstrument(curRegisteredObservable.getObservable());
          it.remove();
        }
      }

      registeredObservables.add(registeredObservable);
    }
  }

  @Override
  public void metricRemoval(KafkaMetric metric) {
    logger.log(Level.FINEST, "Metric removed: {0}", metric.metricName());
    synchronized (lock) {
      for (Iterator<RegisteredObservable> it = registeredObservables.iterator(); it.hasNext(); ) {
        RegisteredObservable current = it.next();
        if (current.getKafkaMetricName().equals(metric.metricName())) {
          closeInstrument(current.getObservable());
          it.remove();
        }
      }
    }
  }

  @Override
  public void close() {
    closeAllInstruments();
  }

  private static void closeAllInstruments() {
    synchronized (lock) {
      for (Iterator<RegisteredObservable> it = registeredObservables.iterator(); it.hasNext(); ) {
        closeInstrument(it.next().getObservable());
        it.remove();
      }
    }
  }

  private static void closeInstrument(AutoCloseable observable) {
    try {
      observable.close();
    } catch (Exception e) {
      throw new IllegalStateException("Error occurred closing instrument", e);
    }
  }

  @Override
  public void configure(Map<String, ?> configs) {
    Object openTelemetry = configs.get(CONFIG_KEY_OPENTELEMETRY_INSTANCE);
    if (openTelemetry == null) {
      throw new IllegalStateException(
          "Missing required configuration property: " + CONFIG_KEY_OPENTELEMETRY_INSTANCE);
    }
    if (!(openTelemetry instanceof OpenTelemetry)) {
      throw new IllegalStateException(
          "Configuration property "
              + CONFIG_KEY_OPENTELEMETRY_INSTANCE
              + " is not instance of OpenTelemetry");
    }
    meter = ((OpenTelemetry) openTelemetry).getMeter("io.opentelemetry.kafka-clients");
  }
}
