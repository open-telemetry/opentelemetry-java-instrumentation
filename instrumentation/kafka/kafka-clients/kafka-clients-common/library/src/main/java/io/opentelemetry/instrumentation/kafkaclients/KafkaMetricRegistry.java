/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static io.opentelemetry.instrumentation.kafkaclients.InstrumentDescriptor.INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER;
import static io.opentelemetry.instrumentation.kafkaclients.InstrumentDescriptor.INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** A registry mapping kafka metrics to corresponding OpenTelemetry metric definitions. */
class KafkaMetricRegistry {

  private static final Set<String> groups = new HashSet<>(Arrays.asList("consumer", "producer"));
  private static final Map<Class<?>, String> measureableToInstrumentType = new HashMap<>();
  private static final Map<String, String> descriptionCache = new ConcurrentHashMap<>();

  static {
    Map<String, String> classNameToType = new HashMap<>();
    classNameToType.put(
        "org.apache.kafka.common.metrics.stats.Rate",
        INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
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
  static InstrumentDescriptor getInstrumentDescriptor(KafkaMetricId kafkaMetricId) {
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
    String description =
        descriptionCache.computeIfAbsent(instrumentName, s -> kafkaMetricId.getDescription());
    String instrumentType =
        measureableToInstrumentType.getOrDefault(
            kafkaMetricId.getMeasureable(), INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);

    switch (instrumentType) {
      case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE:
        return InstrumentDescriptor.createDoubleGauge(instrumentName, description);
      case INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER:
        return InstrumentDescriptor.createDoubleCounter(instrumentName, description);
      default: // Continue below to throw
    }
    throw new IllegalStateException("Unrecognized instrument type. This is a bug.");
  }

  private KafkaMetricRegistry() {}
}
