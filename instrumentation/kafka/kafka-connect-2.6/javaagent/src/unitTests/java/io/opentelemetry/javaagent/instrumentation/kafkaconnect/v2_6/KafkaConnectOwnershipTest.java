/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed;
import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanSuppression;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.util.VirtualField;
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
    processSpanSuppression().restore(null);
  }

  @Test
  void workerPollClaimsBatchAndRestoresNestedRawKafkaProcessing() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchState state = new KafkaConsumerBatchState(true);
    BATCH_STATE.set(records, state);

    Boolean previous = WorkerSinkTaskInstrumentation.PollConsumerAdvice.onEnter();
    assertThat(previous).isNull();
    assertThat(isProcessSpanSuppressed()).isTrue();
    assertThat(processSpanSuppression().get()).isTrue();

    WorkerSinkTaskInstrumentation.PollConsumerAdvice.onExit(previous, records);

    assertThat(state.getAsBoolean()).isFalse();
    assertThat(isProcessSpanSuppressed()).isFalse();
    assertThat(processSpanSuppression().get()).isNull();
  }

  @Test
  void workerPollFailureRestoresNestedRawKafkaProcessing() {
    Boolean previous = WorkerSinkTaskInstrumentation.PollConsumerAdvice.onEnter();
    assertThat(previous).isNull();

    WorkerSinkTaskInstrumentation.PollConsumerAdvice.onExit(previous, null);

    assertThat(isProcessSpanSuppressed()).isFalse();
    assertThat(processSpanSuppression().get()).isNull();
  }

  @Test
  void nestedPollFailurePreservesOuterSuppression() {
    assertThat(processSpanSuppression().set(Boolean.TRUE)).isNull();

    Boolean outer = WorkerSinkTaskInstrumentation.PollConsumerAdvice.onEnter();
    Boolean inner = WorkerSinkTaskInstrumentation.PollConsumerAdvice.onEnter();
    assertThat(outer).isTrue();
    assertThat(inner).isTrue();

    WorkerSinkTaskInstrumentation.PollConsumerAdvice.onExit(inner, null);
    assertThat(processSpanSuppression().get()).isTrue();

    ConsumerRecords<String, String> records = records();
    WorkerSinkTaskInstrumentation.PollConsumerAdvice.onExit(outer, records);
    assertThat(processSpanSuppression().get()).isTrue();
    assertThat(BATCH_STATE.get(records).getAsBoolean()).isFalse();
  }

  @Test
  void receiveOwnershipIsConsumedEvenWhenProcessTelemetryDoesNotStart() {
    SinkRecord record = new SinkRecord("topic", 0, null, null, null, null, 0);
    RECEIVE_OWNED.set(record, true);

    KafkaConnectTask firstAttempt = new KafkaConnectTask(singletonList(record));

    assertThat(firstAttempt.countUnmarkedRecords()).isZero();
    assertThat(new KafkaConnectTask(singletonList(record)).countUnmarkedRecords()).isEqualTo(1);
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("topic", 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "value");
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record)));
  }
}
