/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanSuppression;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KafkaStreamsSuppressionTest {

  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  @AfterEach
  void releaseProcessSpanSuppression() {
    if (processSpanSuppression().isActive()) {
      processSpanSuppression().release();
    }
  }

  @Test
  void pollSuppressesOnlyProcessSpanAndClaimsBatch() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchState state = new KafkaConsumerBatchState(true);
    BATCH_STATE.set(records, state);

    boolean suppressionAcquired = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    assertThat(suppressionAcquired).isTrue();
    assertThat(processSpanSuppression().isActive()).isTrue();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(suppressionAcquired, records);

    assertThat(state.getAsBoolean()).isFalse();
    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void pollFailureRestoresSuppression() {
    boolean suppressionAcquired = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    assertThat(suppressionAcquired).isTrue();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(suppressionAcquired, null);

    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void standbyUpdateSuppressesOnlyProcessSpanAndRestoresSuppression() {
    boolean suppressionAcquired = StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onEnter();
    assertThat(suppressionAcquired).isTrue();
    assertThat(processSpanSuppression().isActive()).isTrue();

    StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onExit(suppressionAcquired);

    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void nestedPollAndStandbyUpdatePreserveOuterSuppression() {
    boolean outer = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    boolean inner = StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onEnter();
    assertThat(outer).isTrue();
    assertThat(inner).isFalse();

    StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onExit(inner);
    assertThat(processSpanSuppression().isActive()).isTrue();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(outer, null);
    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("topic", 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "value");
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record)));
  }
}
