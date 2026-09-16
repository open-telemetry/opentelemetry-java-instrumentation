/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.currentProcessSpanSuppression;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
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
    currentProcessSpanSuppression().restore(MessagingTelemetrySignals.none());
  }

  @Test
  void pollSuppressesOnlyProcessSpanAndClaimsBatch() {
    ConsumerRecords<String, String> records = records();
    KafkaConsumerBatchState state = new KafkaConsumerBatchState(true);
    BATCH_STATE.set(records, state);

    MessagingTelemetrySignals previous = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    assertThat(currentProcessSpanSuppression().current())
        .isEqualTo(MessagingTelemetrySignals.of(PROCESS, SPAN));

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(previous, records);

    assertThat(state.getAsBoolean()).isFalse();
    assertThat(currentProcessSpanSuppression().current())
        .isEqualTo(MessagingTelemetrySignals.none());
  }

  @Test
  void pollFailureRestoresSuppression() {
    MessagingTelemetrySignals previous = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(previous, null);

    assertThat(currentProcessSpanSuppression().current())
        .isEqualTo(MessagingTelemetrySignals.none());
  }

  @Test
  void standbyUpdateSuppressesOnlyProcessSpanAndRestoresSuppression() {
    MessagingTelemetrySignals previous =
        StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onEnter();
    assertThat(currentProcessSpanSuppression().current())
        .isEqualTo(MessagingTelemetrySignals.of(PROCESS, SPAN));

    StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onExit(previous);

    assertThat(currentProcessSpanSuppression().current())
        .isEqualTo(MessagingTelemetrySignals.none());
  }

  @Test
  void nestedPollAndStandbyUpdatePreserveOuterSuppression() {
    MessagingTelemetrySignals initial =
        MessagingTelemetrySignals.of(RECEIVE, SPAN).with(PROCESS, CONSUMED_MESSAGES);
    currentProcessSpanSuppression().restore(initial);

    MessagingTelemetrySignals outer = StreamThreadInstrumentation.PollRequestsAdvice.onEnter();
    MessagingTelemetrySignals inner = StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onEnter();

    StreamThreadInstrumentation.StandbyTaskUpdateAdvice.onExit(inner);
    assertThat(currentProcessSpanSuppression().current()).isEqualTo(initial.with(PROCESS, SPAN));

    StreamThreadInstrumentation.PollRequestsAdvice.onExit(outer, null);
    assertThat(currentProcessSpanSuppression().current()).isEqualTo(initial);
  }

  private static ConsumerRecords<String, String> records() {
    TopicPartition partition = new TopicPartition("topic", 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0, "key", "value");
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record)));
  }
}
