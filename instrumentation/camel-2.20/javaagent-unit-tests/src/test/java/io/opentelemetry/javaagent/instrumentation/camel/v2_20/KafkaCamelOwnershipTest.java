/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaCamelOwnershipTest {

  private static final MessagingTelemetryCarrier<ConsumerRecord<?, ?>> RECORD_TELEMETRY =
      KafkaEndpointInstrumentation.CreateExchangeAdvice.recordTelemetry();
  private static final VirtualField<ConsumerRecords<?, ?>, KafkaConsumerBatchState> BATCH_STATE =
      VirtualField.find(ConsumerRecords.class, KafkaConsumerBatchState.class);

  @Test
  void claimsCamelBatchAfterKafkaRecordsPoll() {
    KafkaConsumer<?, ?> consumer = mock(KafkaConsumer.class);
    ConsumerRecords<?, ?> records = consumerRecords();
    KafkaConsumerBatchState state = new KafkaConsumerBatchState(false);
    BATCH_STATE.set(records, state);
    state.recordPoll(true);
    assertThat(state.getAsBoolean()).isTrue();

    CamelKafkaBatchState.markConsumer(consumer);
    KafkaConsumerInstrumentation.PollAdvice.onExit(consumer, records);
    assertThat(BATCH_STATE.get(records)).isSameAs(state);
    assertThat(state.getAsBoolean()).isFalse();
  }

  @Test
  void claimsCamelBatchBeforeKafkaRecordsPoll() {
    KafkaConsumer<?, ?> consumer = mock(KafkaConsumer.class);
    ConsumerRecords<?, ?> records = consumerRecords();

    CamelKafkaBatchState.markConsumer(consumer);
    KafkaConsumerInstrumentation.PollAdvice.onExit(consumer, records);
    KafkaConsumerBatchState state = BATCH_STATE.get(records);
    assertThat(state).isNotNull();
    assertThat(state.getAsBoolean()).isFalse();

    state.recordPoll(true);

    assertThat(BATCH_STATE.get(records)).isSameAs(state);
    assertThat(state.getAsBoolean()).isFalse();
  }

  @Test
  void doesNotClaimUnmarkedConsumerBatch() {
    KafkaConsumer<?, ?> consumer = mock(KafkaConsumer.class);
    ConsumerRecords<?, ?> records = consumerRecords();

    KafkaConsumerInstrumentation.PollAdvice.onExit(consumer, records);

    assertThat(BATCH_STATE.get(records)).isNull();
  }

  @Test
  void transfersAndConsumesRecordTelemetry() {
    ConsumerRecord<?, ?> record = consumerRecord();
    RECORD_TELEMETRY.add(record, RECEIVE, CONSUMED_MESSAGES);
    Message message = mock(Message.class);
    CamelMessageTelemetry.messageTelemetry().add(message, PROCESS, SPAN);
    Exchange exchange = mock(Exchange.class);
    when(exchange.getIn()).thenReturn(message);

    KafkaEndpointInstrumentation.CreateExchangeAdvice.onExit(record, exchange);

    assertThat(
            CamelMessageTelemetry.messageTelemetry().contains(message, RECEIVE, CONSUMED_MESSAGES))
        .isTrue();
    assertThat(CamelMessageTelemetry.messageTelemetry().contains(message, PROCESS, SPAN)).isFalse();
    assertThat(RECORD_TELEMETRY.contains(record, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  @Test
  void conversionFailureConsumesRecordTelemetry() {
    ConsumerRecord<?, ?> record = consumerRecord();
    RECORD_TELEMETRY.add(record, RECEIVE, CONSUMED_MESSAGES);

    KafkaEndpointInstrumentation.CreateExchangeAdvice.onExit(record, null);

    assertThat(RECORD_TELEMETRY.contains(record, RECEIVE, CONSUMED_MESSAGES)).isFalse();
  }

  private static ConsumerRecords<?, ?> consumerRecords() {
    TopicPartition partition = new TopicPartition("test", 0);
    return new ConsumerRecords<>(singletonMap(partition, singletonList(consumerRecord())));
  }

  private static ConsumerRecord<?, ?> consumerRecord() {
    return new ConsumerRecord<>("test", 0, 0, "key", "value");
  }
}
