/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.TopicPartitionOffset;

class SpringKafkaMockConsumerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void selectsReturnedBatchButNotNestedPoll(boolean batchListener) throws InterruptedException {
    boolean springDisabled = Boolean.getBoolean("springDisabled");
    TopicPartition partition = new TopicPartition("orders", 0);
    AtomicReference<Iterator<ConsumerRecord<String, String>>> earlyIterator =
        new AtomicReference<>();
    MockConsumer<String, String> consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public synchronized ConsumerRecords<String, String> poll(Duration timeout) {
            ConsumerRecords<String, String> records = super.poll(timeout);
            if (!records.isEmpty()) {
              earlyIterator.set(records.iterator());
            }
            return records;
          }
        };
    consumer.updateBeginningOffsets(singletonMap(partition, 0L));
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "outer")));

    ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
    when(factory.createConsumer(any(), any(), any(), any())).thenReturn(consumer);
    ContainerProperties properties = new ContainerProperties(new TopicPartitionOffset("orders", 0));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    CountDownLatch processed = new CountDownLatch(1);
    AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
    MessageListener<String, String> listener =
        record -> {
          try {
            Span listenerSpan = Span.current();
            if (!springDisabled || !batchListener) {
              assertThat(listenerSpan.getSpanContext().isValid()).isTrue();
            }
            if (!springDisabled) {
              assertThat(earlyIterator.get().next()).isSameAs(record);
              assertThat(earlyIterator.get().hasNext()).isFalse();
              assertThat(Span.current().getSpanContext().getSpanId())
                  .isEqualTo(listenerSpan.getSpanContext().getSpanId());
            }
            try (MockConsumer<String, String> nested =
                new MockConsumer<>(OffsetResetStrategy.EARLIEST)) {
              nested.assign(singletonList(partition));
              nested.updateBeginningOffsets(singletonMap(partition, 1L));
              nested.seek(partition, 1L);
              nested.addRecord(new ConsumerRecord<>("orders", 0, 1, "key", "nested"));
              for (ConsumerRecord<String, String> nestedRecord : nested.poll(Duration.ZERO)) {
                assertThat(nestedRecord.value()).isEqualTo("nested");
                assertThat(Span.current().getSpanContext().getSpanId())
                    .isNotEqualTo(listenerSpan.getSpanContext().getSpanId());
              }
            }
            assertThat(Span.current().getSpanContext().getSpanId())
                .isEqualTo(listenerSpan.getSpanContext().getSpanId());
          } catch (Throwable t) {
            listenerFailure.set(t);
          } finally {
            processed.countDown();
          }
        };
    properties.setMessageListener(
        batchListener
            ? (BatchMessageListener<String, String>) records -> records.forEach(listener::onMessage)
            : listener);
    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(factory, properties);
    try {
      container.start();
      assertThat(processed.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    assertThat(listenerFailure.get()).isNull();
    assertThat(testing.spans()).filteredOn(span -> span.getKind() == SpanKind.CONSUMER).hasSize(2);
    assertThat(testing.spans())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.spring-kafka-2.7"))
        .hasSize(springDisabled ? 0 : 1);
    assertThat(testing.spans())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo()
                    .getName()
                    .equals("io.opentelemetry.kafka-clients-0.11"))
        .hasSize(springDisabled ? 2 : 1);
  }
}
