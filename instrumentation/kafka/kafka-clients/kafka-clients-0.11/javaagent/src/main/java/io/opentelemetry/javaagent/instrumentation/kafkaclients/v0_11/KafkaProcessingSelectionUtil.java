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

public final class KafkaProcessingSelectionUtil {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState>
      PROCESSING_SELECTION =
          VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  public static void recordPoll(ConsumerRecords<?, ?> records, boolean applicationPoll) {
    if (records.isEmpty()) {
      return;
    }

    PROCESSING_SELECTION.set(records, new KafkaConsumerBatchState(applicationPoll));
    for (ConsumerRecord<?, ?> record : KafkaConsumerContextUtil.getRecords(records)) {
      KafkaConsumerContextUtil.setRawProcessingSelection(
          record, new KafkaConsumerBatchState(applicationPoll));
    }
  }

  public static void selectFrameworkProcessing(ConsumerRecords<?, ?> records) {
    if (records.isEmpty()) {
      return;
    }

    KafkaConsumerBatchState processingSelection = PROCESSING_SELECTION.get(records);
    if (processingSelection == null) {
      processingSelection = new KafkaConsumerBatchState(false);
      PROCESSING_SELECTION.set(records, processingSelection);
    }
    processingSelection.claimProcessSpan();
    for (ConsumerRecord<?, ?> record : KafkaConsumerContextUtil.getRecords(records)) {
      BooleanSupplier recordSelection = KafkaConsumerContextUtil.getRawProcessingSelection(record);
      if (recordSelection instanceof KafkaConsumerBatchState) {
        ((KafkaConsumerBatchState) recordSelection).claimProcessSpan();
      } else {
        KafkaConsumerContextUtil.setRawProcessingSelection(record, processingSelection);
      }
    }
  }

  public static BooleanSupplier rawProcessingSelection(
      ConsumerRecords<?, ?> records, BooleanSupplier processingEnabled) {
    return () -> {
      KafkaConsumerBatchState processingSelection = PROCESSING_SELECTION.get(records);
      return (processingSelection == null || processingSelection.getAsBoolean())
          && processingEnabled.getAsBoolean();
    };
  }

  private KafkaProcessingSelectionUtil() {}
}
