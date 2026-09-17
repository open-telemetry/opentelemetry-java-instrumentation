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
  void restoreProcessTracing() {
    processSpanSuppression().restore(null);
  }

  @Test
  void pollSuppressesOnlyProcessSpanAndClaimsBatch() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchState state = new KafkaConsumerBatchState(true);
    BATCH_STATE.set(records, state);

    Boolean previous = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    assertThat(previous).isNull();
    assertThat(processSpanSuppression().get()).isTrue();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(previous, records);

    assertThat(state.getAsBoolean()).isFalse();
    assertThat(processSpanSuppression().get()).isNull();
  }

  @Test
  void pollFailureRestoresSuppression() {
    Boolean previous = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    assertThat(previous).isNull();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(previous, null);

    assertThat(processSpanSuppression().get()).isNull();
  }

  @Test
  void standbyUpdateSuppressesOnlyProcessSpanAndRestoresSuppression() {
    Boolean previous = StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onEnter();
    assertThat(previous).isNull();
    assertThat(processSpanSuppression().get()).isTrue();

    StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onExit(previous);

    assertThat(processSpanSuppression().get()).isNull();
  }

  @Test
  void nestedPollAndStandbyUpdatePreserveOuterSuppression() {
    assertThat(processSpanSuppression().set(Boolean.TRUE)).isNull();

    Boolean outer = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    Boolean inner = StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onEnter();
    assertThat(outer).isTrue();
    assertThat(inner).isTrue();

    StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onExit(inner);
    assertThat(processSpanSuppression().get()).isTrue();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(outer, null);
    assertThat(processSpanSuppression().get()).isTrue();
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("topic", 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "value");
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record)));
  }
}
