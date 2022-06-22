/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static io.opentelemetry.instrumentation.kafkaclients.InstrumentDescriptor.INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER;
import static io.opentelemetry.instrumentation.kafkaclients.InstrumentDescriptor.INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.kafka.common.metrics.KafkaMetric;

/** A registry mapping kafka metrics to corresponding OpenTelemetry metric definitions. */
class KafkaMetricRegistry {

  private static final Set<String> groups = new HashSet<>(Arrays.asList("consumer", "producer"));
  private static final Map<Class<?>, String> measureableToInstrumentType = new HashMap<>();
  private static final Map<String, String> descriptionCache = new ConcurrentHashMap<>();

  static {
    Map<String, String> classNameToType = new HashMap<>();
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.Rate", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.Avg", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.Max", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.Value", INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.CumulativeSum",
        INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER);
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.CumulativeCount",
        INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER);

    for (Map.Entry<String, String> entry : classNameToType.entrySet()) {
      try {
        measureableToInstrumentType.put(Class.forName(entry.getKey()), entry.getValue());
      } catch (ClassNotFoundException e) {
        // Class doesn't exist in this version of kafka client - skip
      }
    }
  }

  @Nullable
  static RegisteredObservable getRegisteredObservable(
      Meter meter, KafkaMetricId kafkaMetricId, KafkaMetric kafkaMetric) {
    // If metric is not a Measureable, we can't map it to an instrument
    if (kafkaMetricId.getMeasureable() == null) {
      return null;
    }
    Optional<String> matchingGroup =
        groups.stream().filter(group -> kafkaMetricId.getGroup().contains(group)).findFirst();
    // Only map metrics that have a matching group
    if (!matchingGroup.isPresent()) {
      return null;
    }
    String instrumentName =
        "kafka." + matchingGroup.get() + "." + kafkaMetricId.getName().replace("-", "_");
    String instrumentDescription =
        descriptionCache.computeIfAbsent(instrumentName, s -> kafkaMetricId.getDescription());
    String instrumentType =
        measureableToInstrumentType.getOrDefault(
            kafkaMetricId.getMeasureable(), INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);

    InstrumentDescriptor instrumentDescriptor =
        toInstrumentDescriptor(instrumentType, instrumentName, instrumentDescription);
    Attributes attributes = toAttributes(kafkaMetric);
    AutoCloseable observable =
        createObservable(meter, attributes, instrumentDescriptor, kafkaMetric);
    return RegisteredObservable.create(kafkaMetricId, instrumentDescriptor, attributes, observable);
  }

  private static InstrumentDescriptor toInstrumentDescriptor(
      String instrumentType, String instrumentName, String instrumentDescription) {
    switch (instrumentType) {
      case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE:
        return InstrumentDescriptor.createDoubleGauge(instrumentName, instrumentDescription);
      case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER:
        return InstrumentDescriptor.createDoubleCounter(instrumentName, instrumentDescription);
      default: // Continue below to throw
    }
    throw new IllegalStateException("Unrecognized instrument type. This is a bug.");
  }

  static Attributes toAttributes(KafkaMetric kafkaMetric) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    kafkaMetric.metricName().tags().forEach(attributesBuilder::put);
    return attributesBuilder.build();
  }

  private static AutoCloseable createObservable(
      Meter meter,
      Attributes attributes,
      InstrumentDescriptor instrumentDescriptor,
      KafkaMetric kafkaMetric) {
    Consumer<ObservableDoubleMeasurement> callback =
        observableMeasurement -> observableMeasurement.record(kafkaMetric.value(), attributes);
    switch (instrumentDescriptor.getInstrumentType()) {
      case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE:
        return meter
            .gaugeBuilder(instrumentDescriptor.getName())
            .setDescription(instrumentDescriptor.getDescription())
            .buildWithCallback(callback);
      case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER:
        return meter
            .counterBuilder(instrumentDescriptor.getName())
            .setDescription(instrumentDescriptor.getDescription())
            .ofDoubles()
            .buildWithCallback(callback);
      default: // Continue below to throw
    }
    // TODO: add support for other instrument types and value types as needed for new instruments.
    // This should not happen.
    throw new IllegalStateException("Unrecognized instrument type. This is a bug.");
  }

  private KafkaMetricRegistry() {}
}
