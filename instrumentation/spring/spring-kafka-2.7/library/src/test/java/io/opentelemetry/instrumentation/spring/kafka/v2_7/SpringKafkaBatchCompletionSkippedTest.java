/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.BatchAcknowledgingConsumerAwareMessageListener;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
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
  void batchInterceptorIsCleanedUpAfterPoll() throws InterruptedException {
    // Spring Kafka 2.7 doesn't call setupThreadState and clearThreadState on the interceptor
    assumeTrue(hasPollThreadStateHooks());

    runContainer(
        listener -> listener,
        container ->
            container.setBatchInterceptor(telemetry.<String, String>createBatchInterceptor()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span ->
                span.hasName("process orders")
                    .hasKind(SpanKind.CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfying(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process orders")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfying(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)),
                span -> span.hasName("listener").hasParent(trace.getSpan(0))));
  }

  @Test
  void batchMessageListenerOnlyTracesListenerInvocation() throws InterruptedException {
    runContainer(telemetry::createBatchMessageListener, container -> {});

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName("process orders")
                    .hasKind(SpanKind.CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfying(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)),
            span -> span.hasName("listener").hasParent(trace.getSpan(0))));
  }

  @Test
  void instrumentedConsumerIsMarkedBeforeListConversion() throws InterruptedException {
    runInstrumentedContainer(false);
  }

  @Test
  void instrumentedConsumerIsMarkedBeforePollResultListener() throws InterruptedException {
    runInstrumentedContainer(true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked") // generic ConsumerFactory mock
  void manualAcknowledgmentCommitsBatchOffsetsAfterListenerAcknowledges(boolean pollResult)
      throws InterruptedException {
    TopicPartition partition = new TopicPartition("orders", 0);
    CountDownLatch listenerCompleted = new CountDownLatch(1);
    CountDownLatch commitCompleted = new CountDownLatch(1);
    AtomicReference<OffsetAndMetadata> committedBeforeAcknowledgment = new AtomicReference<>();
    AtomicReference<OffsetAndMetadata> committedAfterAcknowledgment = new AtomicReference<>();
    AtomicReference<Acknowledgment> receivedAcknowledgment = new AtomicReference<>();
    AtomicInteger deliveredRecordCount = new AtomicInteger();
    MockConsumer<String, String> consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public synchronized void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
            super.commitSync(offsets);
            committedAfterAcknowledgment.set(super.committed(singleton(partition)).get(partition));
            commitCompleted.countDown();
          }
        };
    consumer.updateBeginningOffsets(singletonMap(partition, 0L));
    consumer.schedulePollTask(() -> {
      consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "first"));
      consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "key", "second"));
    });

    ConsumerFactory<String, String> factory = mock();
    when(factory.isAutoCommit()).thenReturn(false);
    when(factory.createConsumer(any(), any(), any(), any()))
        .thenReturn(telemetry.instrument(consumer));

    BatchMessageListener<String, String> listener;
    if (pollResult) {
      listener =
          new BatchMessageListener<String, String>() {
            @Override
            public void onMessage(List<ConsumerRecord<String, String>> records) {
              throw new UnsupportedOperationException();
            }

            @Override
            public void onMessage(
                ConsumerRecords<String, String> records,
                Acknowledgment acknowledgment,
                org.apache.kafka.clients.consumer.Consumer<String, String> listenerConsumer) {
              acknowledgeBatch(
                  records.count(),
                  acknowledgment,
                  consumer,
                  partition,
                  committedBeforeAcknowledgment,
                  receivedAcknowledgment,
                  deliveredRecordCount,
                  listenerCompleted);
            }

            @Override
            public boolean wantsPollResult() {
              return true;
            }
          };
    } else {
      listener =
          (BatchAcknowledgingConsumerAwareMessageListener<String, String>)
              (records, acknowledgment, listenerConsumer) ->
                  acknowledgeBatch(
                      records.size(),
                      acknowledgment,
                      consumer,
                      partition,
                      committedBeforeAcknowledgment,
                      receivedAcknowledgment,
                      deliveredRecordCount,
                      listenerCompleted);
    }

    ContainerProperties properties = new ContainerProperties(new TopicPartitionOffset("orders", 0));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    properties.setAckMode(ContainerProperties.AckMode.MANUAL);
    properties.setMessageListener(telemetry.createBatchMessageListener(listener));
    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(factory, properties);
    try {
      container.start();
      assertThat(listenerCompleted.await(10, SECONDS)).isTrue();
      assertThat(receivedAcknowledgment.get()).isNotNull();
      assertThat(commitCompleted.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    assertThat(deliveredRecordCount).hasValue(2);
    assertThat(committedBeforeAcknowledgment.get()).isNull();
    assertThat(committedAfterAcknowledgment.get().offset()).isEqualTo(2L);
  }

  @Test
  @SuppressWarnings({"deprecation", "removal", "unchecked"})
  void skippedSecondSubBatchIsCleanedUpBeforeNextPoll() throws InterruptedException {
    assumeTrue(hasPollThreadStateHooks());

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
    consumer.schedulePollTask(() -> {
      consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "first"));
      consumer.addRecord(new ConsumerRecord<>("orders", 1, 0, "key", "second"));
    });
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "key", "next")));

    ConsumerFactory<String, String> factory = mock();
    when(factory.createConsumer(any(), any(), any(), any())).thenReturn(consumer);
    PlatformTransactionManager transactionManager = mock();
    when(transactionManager.getTransaction(any()))
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

    assertThat(nonEmptyPollCounts).containsExactly(2, 1);
    assertThat(partitions).hasSize(3);
    assertThat(partitions.get(0)).isNotEqualTo(partitions.get(1));
    assertThat(pollsBeforeIntercept).hasSize(3);
    assertThat(pollsBeforeIntercept.get(1)).isEqualTo(pollsBeforeIntercept.get(0));
    assertThat(pollsBeforeIntercept.get(2)).isGreaterThan(pollsBeforeIntercept.get(1));
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
                span -> span.hasName("intercept 0").hasParent(trace.getSpan(0)),
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
                                MESSAGING_DESTINATION_PARTITION_ID, partitions.get(1).toString())),
                span -> span.hasName("intercept 1").hasParent(trace.getSpan(0))),
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
                span -> span.hasName("intercept 2").hasParent(trace.getSpan(0)),
                span -> span.hasName("next listener").hasParent(trace.getSpan(0))));
  }

  @SuppressWarnings({"deprecation", "removal", "unchecked"}) // setTransactionManager, generic mock
  private static void runContainer(
      Function<BatchMessageListener<String, String>, BatchMessageListener<String, String>>
          listenerWrapper,
      Consumer<KafkaMessageListenerContainer<String, String>> customizer)
      throws InterruptedException {
    runContainer(listenerWrapper, customizer, Function.identity(), false, false);
  }

  @SuppressWarnings({"deprecation", "removal", "unchecked"}) // setTransactionManager, generic mock
  private static void runContainer(
      Function<BatchMessageListener<String, String>, BatchMessageListener<String, String>>
          listenerWrapper,
      Consumer<KafkaMessageListenerContainer<String, String>> customizer,
      Function<
              org.apache.kafka.clients.consumer.Consumer<String, String>,
              org.apache.kafka.clients.consumer.Consumer<String, String>>
          consumerWrapper,
      boolean pollResult,
      boolean instrumentedConsumer)
      throws InterruptedException {
    TopicPartition partition = new TopicPartition("orders", 0);
    List<Context> pollContexts = new CopyOnWriteArrayList<>();
    MockConsumer<String, String> consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public synchronized ConsumerRecords<String, String> poll(Duration timeout) {
            pollContexts.add(Context.current());
            return super.poll(timeout);
          }
        };
    consumer.updateBeginningOffsets(singletonMap(partition, 0L));
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "first")));
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "key", "second")));

    ConsumerFactory<String, String> factory = mock();
    when(factory.createConsumer(any(), any(), any(), any()))
        .thenReturn(consumerWrapper.apply(consumer));

    // the transaction can't be started for the first batch
    PlatformTransactionManager transactionManager = mock();
    when(transactionManager.getTransaction(any()))
        .thenThrow(new CannotCreateTransactionException("first batch"))
        .thenReturn(new SimpleTransactionStatus());

    ContainerProperties properties = new ContainerProperties(new TopicPartitionOffset("orders", 0));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    properties.setTransactionManager(transactionManager);
    CountDownLatch processed = new CountDownLatch(1);
    AtomicReference<Integer> pollsBeforeListener = new AtomicReference<>();
    Runnable listenerInvocation =
        () -> {
          testing.runWithSpan("listener", () -> {});
          pollsBeforeListener.set(pollContexts.size());
          processed.countDown();
        };
    BatchMessageListener<String, String> listener;
    if (pollResult) {
      listener =
          new BatchMessageListener<String, String>() {
            @Override
            public void onMessage(List<ConsumerRecord<String, String>> records) {
              throw new UnsupportedOperationException();
            }

            @Override
            public void onMessage(
                ConsumerRecords<String, String> records,
                Acknowledgment acknowledgment,
                org.apache.kafka.clients.consumer.Consumer<String, String> consumer) {
              assertThat(records.count()).isEqualTo(1);
              for (ConsumerRecord<String, String> record : records) {
                assertThat(record.value()).isNotNull();
              }
              listenerInvocation.run();
            }

            @Override
            public boolean wantsPollResult() {
              return true;
            }
          };
    } else {
      listener =
          records -> {
            listenerInvocation.run();
          };
    }
    properties.setMessageListener(listenerWrapper.apply(listener));
    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(factory, properties);
    container.setInterceptBeforeTx(true);
    customizer.accept(container);
    try {
      container.start();
      assertThat(processed.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    // the span of the skipped batch is not current on the consumer thread during later polls
    assertThat(pollsBeforeListener.get()).isGreaterThanOrEqualTo(2);
    assertThat(pollContexts).allSatisfy(context -> assertThat(context).isSameAs(Context.root()));

    if (instrumentedConsumer) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("poll orders").hasKind(SpanKind.CLIENT)),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("poll orders").hasKind(SpanKind.CLIENT)),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("process orders")
                          .hasKind(SpanKind.CONSUMER)
                          .hasNoParent()
                          .hasAttributesSatisfying(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)),
                  span -> span.hasName("listener").hasParent(trace.getSpan(0))));
    }
  }

  private void runInstrumentedContainer(boolean pollResult) throws InterruptedException {
    KafkaTelemetry kafkaTelemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(true)
            .build();
    runContainer(
        telemetry::createBatchMessageListener,
        container -> {},
        consumer -> telemetry.instrument(kafkaTelemetry.instrument(consumer)),
        pollResult,
        true);
  }

  private static void acknowledgeBatch(
      int recordCount,
      Acknowledgment acknowledgment,
      MockConsumer<String, String> consumer,
      TopicPartition partition,
      AtomicReference<OffsetAndMetadata> committedBeforeAcknowledgment,
      AtomicReference<Acknowledgment> receivedAcknowledgment,
      AtomicInteger deliveredRecordCount,
      CountDownLatch listenerCompleted) {
    receivedAcknowledgment.set(acknowledgment);
    deliveredRecordCount.set(recordCount);
    committedBeforeAcknowledgment.set(consumer.committed(singleton(partition)).get(partition));
    if (acknowledgment != null) {
      acknowledgment.acknowledge();
    }
    listenerCompleted.countDown();
  }

  private static boolean hasPollThreadStateHooks() {
    try {
      Class.forName("org.springframework.kafka.listener.ThreadStateProcessor");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
