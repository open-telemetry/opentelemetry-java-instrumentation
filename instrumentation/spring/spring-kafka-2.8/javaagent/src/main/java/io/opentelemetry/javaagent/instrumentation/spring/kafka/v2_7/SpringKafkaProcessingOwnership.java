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

public final class SpringKafkaProcessingOwnership {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  public static void markSpringKafkaAsProcessingOwner(ConsumerRecords<?, ?> records) {
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
      BooleanSupplier rawProcessingEligibility =
          KafkaConsumerContextUtil.getRawProcessingEligibility(record);
      if (rawProcessingEligibility instanceof KafkaConsumerBatchState) {
        ((KafkaConsumerBatchState) rawProcessingEligibility)
            .markProcessingOwnedOutsideKafkaClient();
      } else {
        KafkaConsumerContextUtil.setRawProcessingEligibility(record, batchState);
      }
    }
  }

  private SpringKafkaProcessingOwnership() {}
}
