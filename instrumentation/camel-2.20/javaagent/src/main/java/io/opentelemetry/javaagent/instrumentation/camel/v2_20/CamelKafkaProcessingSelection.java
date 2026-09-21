/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaProcessingSelectionUtil;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public final class CamelKafkaProcessingSelection {

  private static final VirtualField<KafkaConsumer<?, ?>, Boolean> CAMEL_CONSUMERS =
      VirtualField.find(KafkaConsumer.class, Boolean.class);

  public static void markConsumer(KafkaConsumer<?, ?> consumer) {
    CAMEL_CONSUMERS.set(consumer, true);
  }

  public static void selectFrameworkProcessing(
      KafkaConsumer<?, ?> consumer, ConsumerRecords<?, ?> records) {
    if (CAMEL_CONSUMERS.get(consumer) == null || records.isEmpty()) {
      return;
    }
    KafkaProcessingSelectionUtil.selectFrameworkProcessing(records);
  }

  private CamelKafkaProcessingSelection() {}
}
