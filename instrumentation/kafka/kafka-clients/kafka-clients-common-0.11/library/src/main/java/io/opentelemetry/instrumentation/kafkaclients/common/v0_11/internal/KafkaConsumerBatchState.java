/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * Tracks process telemetry ownership for one {@link ConsumerRecords} delivery.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class KafkaConsumerBatchState implements BooleanSupplier {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  private final boolean applicationPoll;
  private volatile boolean processSpanClaimed;

  public static void recordPoll(ConsumerRecords<?, ?> records, boolean applicationPoll) {
    if (!records.isEmpty()) {
      BATCH_STATE.set(records, new KafkaConsumerBatchState(applicationPoll));
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
    state.processSpanClaimed = true;
  }

  public static BooleanSupplier processSpanEnabled(
      ConsumerRecords<?, ?> records, BooleanSupplier defaultValue) {
    KafkaConsumerBatchState state = BATCH_STATE.get(records);
    return state != null ? state : defaultValue;
  }

  private KafkaConsumerBatchState(boolean applicationPoll) {
    this.applicationPoll = applicationPoll;
  }

  @Override
  public boolean getAsBoolean() {
    return applicationPoll && !processSpanClaimed;
  }
}
