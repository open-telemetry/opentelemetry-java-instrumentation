/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class KafkaConsumerBatchStateUtil {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  public static void recordPoll(ConsumerRecords<?, ?> records, @Nullable Boolean previous) {
    if (!records.isEmpty()) {
      BATCH_STATE.set(records, new KafkaConsumerBatchState(previous == null));
    }
  }

  public static void claimProcessSpan(ConsumerRecords<?, ?> records) {
    if (records.isEmpty()) {
      return;
    }

    KafkaConsumerBatchState state = BATCH_STATE.get(records);
    if (state == null) {
      state = new KafkaConsumerBatchState(false);
      BATCH_STATE.set(records, state);
    }
    state.claimProcessSpan();
  }

  public static BooleanSupplier processSpanEnabled(
      ConsumerRecords<?, ?> records, BooleanSupplier defaultValue) {
    KafkaConsumerBatchState state = BATCH_STATE.get(records);
    return state != null ? () -> state.getAsBoolean() && defaultValue.getAsBoolean() : defaultValue;
  }

  private KafkaConsumerBatchStateUtil() {}
}
