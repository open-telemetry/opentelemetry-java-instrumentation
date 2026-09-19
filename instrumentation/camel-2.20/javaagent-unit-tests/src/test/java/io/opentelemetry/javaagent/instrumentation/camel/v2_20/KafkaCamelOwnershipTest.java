/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaConsumerBatchState;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaRecordDeliveryState;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaCamelOwnershipTest {

  private static final VirtualField<ConsumerRecord<?, ?>, KafkaRecordDeliveryState>
      RECORD_DELIVERY_STATE =
          KafkaEndpointInstrumentation.CreateExchangeAdvice.recordDeliveryState();
  private static final VirtualField<Message, KafkaRecordDeliveryState> CAMEL_DELIVERY_STATE =
      KafkaEndpointInstrumentation.CreateExchangeAdvice.camelDeliveryState();
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
  void transfersRecordDeliveryStateAndClearsSource() {
    ConsumerRecord<?, ?> record = consumerRecord();
    KafkaRecordDeliveryState state = recordedDeliveryState();
    RECORD_DELIVERY_STATE.set(record, state);
    Message message = mock(Message.class);
    KafkaRecordDeliveryState staleState = recordedDeliveryState();
    CAMEL_DELIVERY_STATE.set(message, staleState);
    Exchange exchange = mock(Exchange.class);
    when(exchange.getIn()).thenReturn(message);

    KafkaEndpointInstrumentation.CreateExchangeAdvice.onExit(record, exchange);

    assertThat(CAMEL_DELIVERY_STATE.get(message)).isSameAs(state).isNotSameAs(staleState);
    assertThat(CamelMessageTelemetry.getKafkaDeliveryState(message)).isSameAs(state);
    assertThat(RECORD_DELIVERY_STATE.get(record)).isNull();
  }

  @Test
  void clearsRecordDeliveryStateWhenExchangeCreationReturnsNull() {
    ConsumerRecord<?, ?> record = consumerRecord();
    RECORD_DELIVERY_STATE.set(record, recordedDeliveryState());

    KafkaEndpointInstrumentation.CreateExchangeAdvice.onExit(record, null);

    assertThat(RECORD_DELIVERY_STATE.get(record)).isNull();
  }

  @Test
  void clearsRecordDeliveryStateWhenMessageLookupFails() {
    ConsumerRecord<?, ?> record = consumerRecord();
    RECORD_DELIVERY_STATE.set(record, recordedDeliveryState());
    Exchange exchange = mock(Exchange.class);
    when(exchange.getIn()).thenThrow(new IllegalStateException("test"));

    assertThatThrownBy(
            () -> KafkaEndpointInstrumentation.CreateExchangeAdvice.onExit(record, exchange))
        .isInstanceOf(IllegalStateException.class);

    assertThat(RECORD_DELIVERY_STATE.get(record)).isNull();
  }

  @Test
  void clearsStaleCamelDeliveryStateWhenRecordWasNotCounted() {
    ConsumerRecord<?, ?> record = consumerRecord();
    Message message = mock(Message.class);
    CAMEL_DELIVERY_STATE.set(message, recordedDeliveryState());
    Exchange exchange = mock(Exchange.class);
    when(exchange.getIn()).thenReturn(message);

    KafkaEndpointInstrumentation.CreateExchangeAdvice.onExit(record, exchange);

    assertThat(CAMEL_DELIVERY_STATE.get(message)).isNull();
  }

  private static ConsumerRecords<?, ?> consumerRecords() {
    TopicPartition partition = new TopicPartition("test", 0);
    return new ConsumerRecords<>(singletonMap(partition, singletonList(consumerRecord())));
  }

  private static ConsumerRecord<?, ?> consumerRecord() {
    return new ConsumerRecord<>("test", 0, 0, "key", "value");
  }

  private static KafkaRecordDeliveryState recordedDeliveryState() {
    KafkaRecordDeliveryState state = new KafkaRecordDeliveryState();
    state.markConsumedMessagesRecorded();
    return state;
  }
}
