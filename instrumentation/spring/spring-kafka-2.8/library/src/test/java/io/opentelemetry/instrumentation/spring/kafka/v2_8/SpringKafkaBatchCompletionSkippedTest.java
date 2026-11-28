/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;

class SpringKafkaBatchCompletionSkippedTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  @SuppressWarnings({"deprecation", "removal", "unchecked"})
  void transactionStartupFailureCleansUpEverySubBatchBeforeNextPoll()
      throws InterruptedException, ReflectiveOperationException {
    TopicPartition first = new TopicPartition("orders", 0);
    TopicPartition second = new TopicPartition("orders", 1);
    List<Integer> pollCounts = new CopyOnWriteArrayList<>();
    AtomicInteger polls = new AtomicInteger();
    MockConsumer<String, String> consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public ConsumerGroupMetadata groupMetadata() {
            return new ConsumerGroupMetadata("group");
          }

          @Override
          public synchronized ConsumerRecords<String, String> poll(Duration timeout) {
            polls.incrementAndGet();
            ConsumerRecords<String, String> records = super.poll(timeout);
            if (!records.isEmpty()) {
              int index = pollCounts.size();
              testing.runWithSpan("poll " + index, () -> {});
              pollCounts.add(records.count());
            }
            return records;
          }
        };
    Map<TopicPartition, Long> offsets = new HashMap<>();
    offsets.put(first, 0L);
    offsets.put(second, 0L);
    consumer.updateBeginningOffsets(offsets);
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "skipped")));
    consumer.schedulePollTask(() -> {
      consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "key", "first"));
      consumer.addRecord(new ConsumerRecord<>("orders", 1, 0, "key", "second"));
    });
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 2, "key", "next")));

    ConsumerFactory<String, String> factory = mock();
    when(factory.createConsumer(any(), any(), any(), any())).thenReturn(consumer);
    ProducerFactory<String, String> producerFactory = mock();
    when(producerFactory.transactionCapable()).thenReturn(true);
    Producer<String, String> producer = mock();
    when(producerFactory.createProducer(any()))
        .thenThrow(new CannotCreateTransactionException("first batch"))
        .thenReturn(producer)
        .thenThrow(new CannotCreateTransactionException("second sub-batch"))
        .thenReturn(producer);
    KafkaTransactionManager<String, String> transactionManager =
        new KafkaTransactionManager<>(producerFactory);

    ContainerProperties properties =
        new ContainerProperties(
            new TopicPartitionOffset("orders", 0), new TopicPartitionOffset("orders", 1));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    setTransactionManager(properties, transactionManager);
    properties.setSubBatchPerPartition(true);
    CountDownLatch processed = new CountDownLatch(2);
    AtomicInteger listenerInvocations = new AtomicInteger();
    properties.setMessageListener(
        (BatchMessageListener<String, String>)
            records -> {
              String name =
                  listenerInvocations.getAndIncrement() == 0 ? "first listener" : "next listener";
              testing.runWithSpan(name, () -> {});
              processed.countDown();
            });
    List<Integer> partitions = new CopyOnWriteArrayList<>();
    List<Integer> pollsBeforeIntercept = new CopyOnWriteArrayList<>();
    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(factory, properties);
    container.setInterceptBeforeTx(true);
    container.setBatchInterceptor(
        SpringKafkaTelemetry.create(testing.getOpenTelemetry())
            .createBatchInterceptor((records, ignored) -> {
              int index = partitions.size();
              partitions.add(records.partitions().iterator().next().partition());
              pollsBeforeIntercept.add(polls.get());
              testing.runWithSpan("intercept " + index, () -> {});
              return records;
            }));
    try {
      container.start();
      assertThat(processed.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    assertThat(pollCounts).containsExactly(1, 2, 1);
    assertThat(partitions).hasSize(4);
    assertThat(partitions.get(1)).isNotEqualTo(partitions.get(2));
    assertThat(pollsBeforeIntercept).hasSize(4);
    assertThat(pollsBeforeIntercept.get(1)).isGreaterThan(pollsBeforeIntercept.get(0));
    assertThat(pollsBeforeIntercept.get(2)).isEqualTo(pollsBeforeIntercept.get(1));
    assertThat(pollsBeforeIntercept.get(3)).isGreaterThan(pollsBeforeIntercept.get(2));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("poll 0").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertProcessSpan(span, partitions.get(0)),
                span -> span.hasName("intercept 0").hasParent(trace.getSpan(0))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("poll 1").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertProcessSpan(span, partitions.get(1)),
                span -> span.hasName("intercept 1").hasParent(trace.getSpan(0)),
                span -> span.hasName("first listener").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertProcessSpan(span, partitions.get(2)),
                span -> span.hasName("intercept 2").hasParent(trace.getSpan(0))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("poll 2").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertProcessSpan(span, partitions.get(3)),
                span -> span.hasName("intercept 3").hasParent(trace.getSpan(0)),
                span -> span.hasName("next listener").hasParent(trace.getSpan(0))));
  }

  private static SpanDataAssert assertProcessSpan(SpanDataAssert span, int partition) {
    return span.hasName("process orders")
        .hasKind(SpanKind.CONSUMER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "orders"),
            equalTo(MESSAGING_DESTINATION_PARTITION_ID, Integer.toString(partition)),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
            equalTo(stringKey("messaging.consumer.group.name"), "group"));
  }

  private static void setTransactionManager(
      ContainerProperties properties, KafkaTransactionManager<?, ?> transactionManager)
      throws ReflectiveOperationException {
    try {
      ContainerProperties.class
          .getMethod("setKafkaAwareTransactionManager", KafkaAwareTransactionManager.class)
          .invoke(properties, transactionManager);
    } catch (NoSuchMethodException ignored) {
      // The replacement setter was added in 3.2; use the supported 2.8 setter at the floor.
      ContainerProperties.class
          .getMethod("setTransactionManager", PlatformTransactionManager.class)
          .invoke(properties, transactionManager);
    }
  }
}
