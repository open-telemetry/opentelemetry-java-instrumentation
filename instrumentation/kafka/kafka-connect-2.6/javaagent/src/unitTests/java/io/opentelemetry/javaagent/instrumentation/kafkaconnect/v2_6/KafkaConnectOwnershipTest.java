/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KafkaConnectOwnershipTest {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);
  private static final VirtualField<SinkRecord, Boolean> RECEIVE_OWNED =
      VirtualField.find(SinkRecord.class, Boolean.class);

  @AfterEach
  void restoreProcessTracing() {
    KafkaClientsConsumerProcessTracing.setWrappingEnabled(true);
  }

  @Test
  void workerPollClaimsBatchAndRestoresNestedRawKafkaProcessing() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchState state = new KafkaConsumerBatchState(true);
    BATCH_STATE.set(records, state);

    boolean previousValue = WorkerSinkTaskInstrumentation.PollConsumerAdvice.onEnter();
    assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isFalse();

    WorkerSinkTaskInstrumentation.PollConsumerAdvice.onExit(previousValue, records);

    assertThat(state.getAsBoolean()).isFalse();
    assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isTrue();
  }

  @Test
  void workerPollFailureRestoresNestedRawKafkaProcessing() {
    boolean previousValue = WorkerSinkTaskInstrumentation.PollConsumerAdvice.onEnter();

    WorkerSinkTaskInstrumentation.PollConsumerAdvice.onExit(previousValue, null);

    assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isTrue();
  }

  @Test
  void receiveOwnershipIsConsumedEvenWhenProcessTelemetryDoesNotStart() {
    SinkRecord record = new SinkRecord("topic", 0, null, null, null, null, 0);
    RECEIVE_OWNED.set(record, true);

    new KafkaConnectTask(singletonList(record));

    assertThat(new KafkaConnectTask(singletonList(record)).countUnmarkedRecords()).isEqualTo(1);
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("topic", 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "value");
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record)));
  }
}
