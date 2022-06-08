/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import com.google.auto.value.AutoValue;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;

/** A value class collecting the identifying fields of a kafka {@link KafkaMetric}. */
@AutoValue
abstract class KafkaMetricId {

  abstract String getGroup();

  abstract String getName();

  abstract String getDescription();

  @Nullable
  abstract Class<? extends Measurable> getMeasureable();

  abstract Set<String> getAttributeKeys();

  static KafkaMetricId create(KafkaMetric kafkaMetric) {
    Class<? extends Measurable> measureable;
    try {
      measureable = kafkaMetric.measurable().getClass();
    } catch (IllegalStateException e) {
      measureable = null;
    }
    return new AutoValue_KafkaMetricId(
        kafkaMetric.metricName().group(),
        kafkaMetric.metricName().name(),
        kafkaMetric.metricName().description(),
        measureable,
        kafkaMetric.metricName().tags().keySet());
  }
}
