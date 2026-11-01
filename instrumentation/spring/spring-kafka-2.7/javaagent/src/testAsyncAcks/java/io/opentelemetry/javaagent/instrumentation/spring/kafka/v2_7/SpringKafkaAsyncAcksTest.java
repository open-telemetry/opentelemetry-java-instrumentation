/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.TopicPartitionOffset;

class SpringKafkaAsyncAcksTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  // With async acks the container iterates each polled batch to record its offsets before
  // handing it to the listener. That iteration must not create Kafka client process spans.
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void doesNotCreateClientProcessSpansWhenAcksAreAsync(boolean batchListener)
      throws InterruptedException {
    TopicPartition partition = new TopicPartition("orders", 0);
    MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    consumer.updateBeginningOffsets(singletonMap(partition, 0L));
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "value")));

    ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
    when(factory.createConsumer(any(), any(), any(), any())).thenReturn(consumer);
    ContainerProperties properties = new ContainerProperties(new TopicPartitionOffset("orders", 0));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    properties.setAckMode(ContainerProperties.AckMode.MANUAL);
    properties.setAsyncAcks(true);
    CountDownLatch processed = new CountDownLatch(1);
    properties.setMessageListener(
        batchListener
            ? (BatchAcknowledgingMessageListener<String, String>)
                (records, ack) -> {
                  ack.acknowledge();
                  processed.countDown();
                }
            : (AcknowledgingMessageListener<String, String>)
                (record, ack) -> {
                  ack.acknowledge();
                  processed.countDown();
                });
    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(factory, properties);
    try {
      container.start();
      assertThat(processed.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    assertThat(testing.spans())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.kafka-clients-0.11"))
        .isEmpty();
    assertThat(testing.spans())
        .filteredOn(span -> span.getKind() == SpanKind.CONSUMER)
        .singleElement()
        .satisfies(
            span ->
                assertThat(span.getInstrumentationScopeInfo().getName())
                    .isEqualTo("io.opentelemetry.spring-kafka-2.7"));
  }
}
