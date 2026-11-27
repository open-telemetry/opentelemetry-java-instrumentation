/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

// a batch is intercepted before the transaction is started, and the container calls neither
// success nor failure on the batch interceptor when the transaction can't be started
class SpringKafkaBatchCompletionSkippedTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final SpringKafkaTelemetry telemetry =
      SpringKafkaTelemetry.create(testing.getOpenTelemetry());

  @Test
  @SuppressWarnings({"deprecation", "removal", "unchecked"})
  void skippedSecondSubBatchIsCleanedUpBeforeNextPoll() throws InterruptedException {
    TopicPartition first = new TopicPartition("orders", 0);
    TopicPartition second = new TopicPartition("orders", 1);
    List<Context> pollContexts = new CopyOnWriteArrayList<>();
    List<Integer> nonEmptyPollCounts = new CopyOnWriteArrayList<>();
    MockConsumer<String, String> consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public synchronized ConsumerRecords<String, String> poll(Duration timeout) {
            pollContexts.add(Context.current());
            ConsumerRecords<String, String> records = super.poll(timeout);
            if (!records.isEmpty()) {
              nonEmptyPollCounts.add(records.count());
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
    PlatformTransactionManager transactionManager = mock();
    when(transactionManager.getTransaction(any()))
        .thenThrow(new CannotCreateTransactionException("first batch"))
        .thenReturn(new SimpleTransactionStatus())
        .thenThrow(new CannotCreateTransactionException("second sub-batch"))
        .thenReturn(new SimpleTransactionStatus());

    ContainerProperties properties =
        new ContainerProperties(
            new TopicPartitionOffset("orders", 0), new TopicPartitionOffset("orders", 1));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    properties.setTransactionManager(transactionManager);
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
        telemetry.createBatchInterceptor((records, ignored) -> {
          int index = partitions.size();
          partitions.add(records.partitions().iterator().next().partition());
          pollsBeforeIntercept.add(pollContexts.size());
          testing.runWithSpan("intercept " + index, () -> {});
          return records;
        }));
    try {
      container.start();
      assertThat(processed.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    assertThat(nonEmptyPollCounts).containsExactly(1, 2, 1);
    assertThat(partitions).hasSize(4);
    assertThat(partitions.get(1)).isNotEqualTo(partitions.get(2));
    assertThat(pollsBeforeIntercept).hasSize(4);
    assertThat(pollsBeforeIntercept.get(1)).isGreaterThan(pollsBeforeIntercept.get(0));
    assertThat(pollsBeforeIntercept.get(2)).isEqualTo(pollsBeforeIntercept.get(1));
    assertThat(pollsBeforeIntercept.get(3)).isGreaterThan(pollsBeforeIntercept.get(2));
    assertThat(pollContexts).allSatisfy(context -> assertThat(context).isSameAs(Context.root()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process orders")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                            equalTo(
                                MESSAGING_DESTINATION_PARTITION_ID, partitions.get(0).toString())),
                span -> span.hasName("intercept 0").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process orders")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                            equalTo(
                                MESSAGING_DESTINATION_PARTITION_ID, partitions.get(1).toString())),
                span -> span.hasName("intercept 1").hasParent(trace.getSpan(0)),
                span -> span.hasName("first listener").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process orders")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                            equalTo(
                                MESSAGING_DESTINATION_PARTITION_ID, partitions.get(2).toString())),
                span -> span.hasName("intercept 2").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process orders")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                            equalTo(
                                MESSAGING_DESTINATION_PARTITION_ID, partitions.get(3).toString())),
                span -> span.hasName("intercept 3").hasParent(trace.getSpan(0)),
                span -> span.hasName("next listener").hasParent(trace.getSpan(0))));
  }
}
