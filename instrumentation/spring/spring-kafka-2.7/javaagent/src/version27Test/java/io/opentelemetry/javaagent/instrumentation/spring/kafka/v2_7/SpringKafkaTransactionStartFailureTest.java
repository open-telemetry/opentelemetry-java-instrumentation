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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class SpringKafkaTransactionStartFailureTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("unchecked")
  void endsEarlyInterceptedSpanWhenTransactionFailsToStart() throws InterruptedException {
    TopicPartition partition = new TopicPartition("orders", 0);
    MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    consumer.updateBeginningOffsets(singletonMap(partition, 0L));
    consumer.schedulePollTask(
        () -> consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "key", "value")));

    ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
    when(factory.createConsumer(any(), any(), any(), any())).thenReturn(consumer);

    CannotCreateTransactionException error = new CannotCreateTransactionException("boom");
    ContainerProperties properties = new ContainerProperties(new TopicPartitionOffset("orders", 0));
    properties.setGroupId("group");
    properties.setPollTimeout(10);
    properties.setTransactionManager(new FailingTransactionManager(error));
    AtomicInteger listenerCalls = new AtomicInteger();
    properties.setMessageListener(
        (MessageListener<String, String>) record -> listenerCalls.incrementAndGet());

    AtomicInteger interceptCalls = new AtomicInteger();
    AtomicInteger successCalls = new AtomicInteger();
    AtomicInteger failureCalls = new AtomicInteger();
    CountDownLatch afterRollback = new CountDownLatch(1);
    AtomicReference<SpanContext> afterRollbackSpanContext = new AtomicReference<>();

    KafkaMessageListenerContainer<String, String> container =
        new KafkaMessageListenerContainer<>(factory, properties);
    container.setInterceptBeforeTx(true);
    container.setRecordInterceptor(
        new RecordInterceptor<String, String>() {
          @Override
          public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record) {
            interceptCalls.incrementAndGet();
            return record;
          }

          @Override
          public void success(
              ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
            successCalls.incrementAndGet();
          }

          @Override
          public void failure(
              ConsumerRecord<String, String> record,
              Exception exception,
              Consumer<String, String> consumer) {
            failureCalls.incrementAndGet();
          }
        });
    // runs on the consumer thread after the transaction failed to start
    container.setAfterRollbackProcessor(
        (records, kafkaConsumer, messageListenerContainer, exception, recoverable, eosMode) -> {
          afterRollbackSpanContext.set(Span.current().getSpanContext());
          afterRollback.countDown();
        });
    try {
      container.start();
      assertThat(afterRollback.await(10, SECONDS)).isTrue();
    } finally {
      container.stop();
    }

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName("process orders")
                .hasKind(SpanKind.CONSUMER)
                .hasNoParent()
                .hasStatus(StatusData.error())
                .hasException(error)));
    assertThat(afterRollbackSpanContext.get().isValid()).isFalse();
    assertThat(interceptCalls.get()).isEqualTo(1);
    assertThat(listenerCalls.get()).isZero();
    assertThat(successCalls.get()).isZero();
    assertThat(failureCalls.get()).isZero();
  }

  private static class FailingTransactionManager implements PlatformTransactionManager {

    private final RuntimeException error;

    FailingTransactionManager(RuntimeException error) {
      this.error = error;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      throw error;
    }

    @Override
    public void commit(TransactionStatus status) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void rollback(TransactionStatus status) {
      throw new UnsupportedOperationException();
    }
  }
}
