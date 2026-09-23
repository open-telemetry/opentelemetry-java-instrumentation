/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class KafkaProcessingOwnershipUtil {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  public static void recordPoll(ConsumerRecords<?, ?> records, boolean applicationPoll) {
    if (records.isEmpty()) {
      return;
    }

    BATCH_STATE.set(records, new KafkaConsumerBatchState(applicationPoll));
    for (ConsumerRecord<?, ?> record : KafkaConsumerContextUtil.getRecords(records)) {
      KafkaConsumerContextUtil.setRawProcessingEligibility(
          record, new KafkaConsumerBatchState(applicationPoll));
    }
  }

  public static void markProcessingOwnedOutsideKafkaClient(ConsumerRecords<?, ?> records) {
    if (records.isEmpty()) {
      return;
    }

    KafkaConsumerBatchState batchState = BATCH_STATE.get(records);
    if (batchState == null) {
      batchState = new KafkaConsumerBatchState(false);
      BATCH_STATE.set(records, batchState);
    }
    batchState.markProcessingOwnedOutsideKafkaClient();
    for (ConsumerRecord<?, ?> record : KafkaConsumerContextUtil.getRecords(records)) {
      BooleanSupplier recordEligibility =
          KafkaConsumerContextUtil.getRawProcessingEligibility(record);
      if (recordEligibility instanceof KafkaConsumerBatchState) {
        ((KafkaConsumerBatchState) recordEligibility).markProcessingOwnedOutsideKafkaClient();
      } else {
        KafkaConsumerContextUtil.setRawProcessingEligibility(record, batchState);
      }
    }
  }

  public static BooleanSupplier rawProcessingEligibility(
      ConsumerRecords<?, ?> records, BooleanSupplier processingEnabled) {
    return () -> {
      KafkaConsumerBatchState batchState = BATCH_STATE.get(records);
      return (batchState == null || batchState.getAsBoolean()) && processingEnabled.getAsBoolean();
    };
  }

  private KafkaProcessingOwnershipUtil() {}
}
