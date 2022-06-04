/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;

@AutoValue
abstract class RegisteredInstrument {

  abstract KafkaMetricId getKafkaMetricId();

  abstract Attributes getAttributes();

  static RegisteredInstrument create(KafkaMetricId kafkaMetricId, Attributes attributes) {
    return new AutoValue_RegisteredInstrument(kafkaMetricId, attributes);
  }
}
