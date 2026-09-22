/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class SpringKafkaProcessingSelection {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState>
      PROCESSING_SELECTION =
          VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  public static void selectListenerProcessing(ConsumerRecords<?, ?> records) {
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
      BooleanSupplier rawProcessingSelection =
          KafkaConsumerContextUtil.getRawProcessingSelection(record);
      if (rawProcessingSelection instanceof KafkaConsumerBatchState) {
        ((KafkaConsumerBatchState) rawProcessingSelection).claimProcessSpan();
      } else {
        KafkaConsumerContextUtil.setRawProcessingSelection(record, processingSelection);
      }
    }
  }

  private SpringKafkaProcessingSelection() {}
}
