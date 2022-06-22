/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static io.opentelemetry.instrumentation.kafkaclients.KafkaMetricRegistry.toAttributes;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import org.apache.kafka.common.metrics.KafkaMetric;

@AutoValue
abstract class RegisteredObservable {

  abstract KafkaMetricId getKafkaMetricId();

  abstract InstrumentDescriptor getInstrumentDescriptor();

  abstract Attributes getAttributes();

  abstract AutoCloseable getObservable();

  boolean matches(KafkaMetric kafkaMetric) {
    return getKafkaMetricId().equals(KafkaMetricId.create(kafkaMetric))
        && getAttributes().equals(toAttributes(kafkaMetric));
  }

  static RegisteredObservable create(
      KafkaMetricId kafkaMetricId,
      InstrumentDescriptor instrumentDescriptor,
      Attributes attributes,
      AutoCloseable observable) {
    return new AutoValue_RegisteredObservable(
        kafkaMetricId, instrumentDescriptor, attributes, observable);
  }
}
