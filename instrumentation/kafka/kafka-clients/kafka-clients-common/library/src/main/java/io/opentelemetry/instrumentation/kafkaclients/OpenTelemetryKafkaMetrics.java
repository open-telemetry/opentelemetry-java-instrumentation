/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;

/**
 * A {@link MetricsReporter} which bridges Kafka metrics to OpenTelemetry metrics.
 *
 * <p>To use, configure OpenTelemetry instance via {@link #setOpenTelemetry(OpenTelemetry)}, and
 * include a reference to this class in kafka producer or consumer configuration, i.e.:
 *
 * <pre>{@code
 * //    Map<String, Object> config = new HashMap<>();
 * //    // Register OpenTelemetryKafkaMetrics as reporter
 * //    config.put(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, OpenTelemetryKafkaMetrics.class.getName());
 * //    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
 * //    ...
 * //    try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(config)) { ... }
 * }</pre>
 */
public class OpenTelemetryKafkaMetrics implements MetricsReporter {

  private static final Logger logger = Logger.getLogger(OpenTelemetryKafkaMetrics.class.getName());
  @Nullable private static volatile Meter meter;

  private static final List<RegisteredObservable> registeredObservables = new ArrayList<>();

  /**
   * Setup OpenTelemetry. This should be called as early in the application lifecycle as possible.
   * Kafka metrics that are observed before this is called may not be bridged.
   */
  public static void setOpenTelemetry(OpenTelemetry openTelemetry) {
    meter = openTelemetry.getMeter("io.opentelemetry.kafka-clients");
  }

  /**
   * Reset for test by reseting the {@link #meter} to {@code null} and closing all registered
   * instruments.
   */
  static void resetForTest() {
    meter = null;
    closeAllInstruments();
  }

  // Visible for test
  static List<RegisteredObservable> getRegisteredObservables() {
    return registeredObservables;
  }

  @Override
  public void init(List<KafkaMetric> metrics) {
    metrics.forEach(this::metricChange);
  }

  @Override
  public synchronized void metricChange(KafkaMetric metric) {
    KafkaMetricId kafkaMetricId = KafkaMetricId.create(metric);
    Meter currentMeter = meter;
    if (currentMeter == null) {
      logger.log(Level.FINEST, "Metric changed but meter not set: {0}", kafkaMetricId);
      return;
    }

    RegisteredObservable registeredObservable =
        KafkaMetricRegistry.getRegisteredObservable(currentMeter, kafkaMetricId, metric);
    if (registeredObservable == null) {
      logger.log(Level.FINEST, "Metric changed but cannot map to instrument: {0}", kafkaMetricId);
      return;
    }

    InstrumentDescriptor instrumentDescriptor = registeredObservable.getInstrumentDescriptor();
    Attributes attributes = registeredObservable.getAttributes();
    Set<AttributeKey<?>> attributeKeys = attributes.asMap().keySet();

    for (Iterator<RegisteredObservable> it = registeredObservables.iterator(); it.hasNext(); ) {
      RegisteredObservable curRegisteredObservable = it.next();
      Set<AttributeKey<?>> curAttributeKeys =
          curRegisteredObservable.getAttributes().asMap().keySet();
      if (curRegisteredObservable.equals(registeredObservable)) {
        logger.log(Level.FINEST, "Replacing instrument: {0}", curRegisteredObservable);
        closeInstrument(curRegisteredObservable.getObservable());
        it.remove();
      } else if (curRegisteredObservable.getInstrumentDescriptor().equals(instrumentDescriptor)
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

  @Override
  public void metricRemoval(KafkaMetric metric) {
    KafkaMetricId kafkaMetricId = KafkaMetricId.create(metric);
    logger.log(Level.FINEST, "Metric removed: {0}", kafkaMetricId);
    for (Iterator<RegisteredObservable> it = registeredObservables.iterator(); it.hasNext(); ) {
      RegisteredObservable current = it.next();
      if (current.matches(metric)) {
        closeInstrument(current.getObservable());
        it.remove();
      }
    }
  }

  @Override
  public void close() {
    closeAllInstruments();
  }

  static void closeAllInstruments() {
    for (Iterator<RegisteredObservable> it = registeredObservables.iterator(); it.hasNext(); ) {
      closeInstrument(it.next().getObservable());
      it.remove();
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
  public void configure(Map<String, ?> configs) {}
}
