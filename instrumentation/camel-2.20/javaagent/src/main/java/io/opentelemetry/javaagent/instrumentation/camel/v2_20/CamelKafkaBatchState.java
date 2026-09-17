/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public final class CamelKafkaBatchState {

  private static final VirtualField<KafkaConsumer<?, ?>, Boolean> CAMEL_CONSUMERS =
      VirtualField.find(KafkaConsumer.class, Boolean.class);
  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  public static void markConsumer(KafkaConsumer<?, ?> consumer) {
    CAMEL_CONSUMERS.set(consumer, true);
  }

  public static void claimBatch(KafkaConsumer<?, ?> consumer, ConsumerRecords<?, ?> records) {
    if (CAMEL_CONSUMERS.get(consumer) == null || records.isEmpty()) {
      return;
    }
    KafkaConsumerBatchState state = BATCH_STATE.get(records);
    if (state == null) {
      state = new KafkaConsumerBatchState(false);
      BATCH_STATE.set(records, state);
    }
    state.claimProcessSpan();
  }

  private CamelKafkaBatchState() {}
}
