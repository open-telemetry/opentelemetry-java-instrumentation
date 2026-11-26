/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
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
            trace.hasSpansSatisfyingExactly(span ->
                span.hasName("process orders")
                    .hasKind(SpanKind.CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfying(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1))));
  }

  @Test
  void batchMessageListenerOnlyTracesListenerInvocation() throws InterruptedException {
    runContainer(telemetry::createBatchMessageListener, container -> {});

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName("process orders")
                .hasKind(SpanKind.CONSUMER)
                .hasNoParent()
                .hasAttributesSatisfying(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1))));
  }

  @SuppressWarnings({"deprecation", "removal", "unchecked"}) // setTransactionManager, generic mock
  private static void runContainer(
      Function<BatchMessageListener<String, String>, BatchMessageListener<String, String>>
          listenerWrapper,
      Consumer<KafkaMessageListenerContainer<String, String>> customizer)
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
    when(factory.createConsumer(any(), any(), any(), any())).thenReturn(consumer);

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
    AtomicReference<Context> listenerContext = new AtomicReference<>();
    AtomicReference<Integer> pollsBeforeListener = new AtomicReference<>();
    BatchMessageListener<String, String> listener =
        records -> {
          listenerContext.set(Context.current());
          pollsBeforeListener.set(pollContexts.size());
          processed.countDown();
        };
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
    // the span of the second batch is current while the listener runs
    assertThat(Span.fromContext(listenerContext.get()).getSpanContext().isValid()).isTrue();
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
