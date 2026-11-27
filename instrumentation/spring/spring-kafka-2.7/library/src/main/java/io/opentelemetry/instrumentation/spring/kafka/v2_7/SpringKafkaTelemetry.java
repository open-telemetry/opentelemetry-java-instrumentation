/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.RecordInterceptor;

/** Entrypoint for instrumenting Spring Kafka listeners. */
public final class SpringKafkaTelemetry {
  private final Instrumenter<KafkaProcessRequest, Void> processInstrumenter;
  private final Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter;

  /** Returns a new {@link SpringKafkaTelemetry} configured with the given {@link OpenTelemetry}. */
  public static SpringKafkaTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringKafkaTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringKafkaTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringKafkaTelemetryBuilder(openTelemetry);
  }

  SpringKafkaTelemetry(
      Instrumenter<KafkaProcessRequest, Void> processInstrumenter,
      Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter) {
    this.processInstrumenter = processInstrumenter;
    this.batchProcessInstrumenter = batchProcessInstrumenter;
  }

  /**
   * Returns a new {@link RecordInterceptor} that decorates a message listener with a {@link
   * SpanKind#CONSUMER CONSUMER} span. Can be set on a {@link AbstractMessageListenerContainer}
   * using the {@link AbstractMessageListenerContainer#setRecordInterceptor(RecordInterceptor)}
   * method.
   */
  public <K, V> RecordInterceptor<K, V> createRecordInterceptor() {
    return createRecordInterceptor(null);
  }

  /**
   * Returns a new {@link RecordInterceptor} that decorates a message listener with a {@link
   * SpanKind#CONSUMER CONSUMER} span, and then delegates to a provided {@code
   * decoratedInterceptor}. Can be set on a {@link AbstractMessageListenerContainer} using the
   * {@link AbstractMessageListenerContainer#setRecordInterceptor(RecordInterceptor)} method.
   */
  public <K, V> RecordInterceptor<K, V> createRecordInterceptor(
      @Nullable RecordInterceptor<K, V> decoratedInterceptor) {
    return new InstrumentedRecordInterceptor<>(processInstrumenter, decoratedInterceptor);
  }

  /**
   * Returns a new {@link BatchInterceptor} that decorates a message listener with a {@link
   * SpanKind#CONSUMER CONSUMER} span. Can be set on a {@link AbstractMessageListenerContainer}
   * using the {@link AbstractMessageListenerContainer#setBatchInterceptor(BatchInterceptor)}
   * method.
   */
  public <K, V> BatchInterceptor<K, V> createBatchInterceptor() {
    return createBatchInterceptor(null);
  }

  /**
   * Returns a new {@link BatchInterceptor} that decorates a message listener with a {@link
   * SpanKind#CONSUMER CONSUMER} span, and then delegates to a provided {@code
   * decoratedInterceptor}. Can be set on a {@link AbstractMessageListenerContainer} using the
   * {@link AbstractMessageListenerContainer#setBatchInterceptor(BatchInterceptor)} method.
   */
  public <K, V> BatchInterceptor<K, V> createBatchInterceptor(
      @Nullable BatchInterceptor<K, V> decoratedInterceptor) {
    return new InstrumentedBatchInterceptor<>(batchProcessInstrumenter, decoratedInterceptor);
  }

  /**
   * Returns a new {@link BatchMessageListener} that wraps an invocation of the provided batch
   * {@code listener} in a {@link SpanKind#CONSUMER CONSUMER} span. The span is ended when the
   * listener returns or throws. A retry using the most recently traced list or {@link
   * ConsumerRecords} object on the same thread and wrapper does not create another processing span;
   * redelivery with a different batch object can be traced separately. Batches that never reach the
   * listener are not traced. Can be set on a {@link AbstractMessageListenerContainer} using the
   * {@link AbstractMessageListenerContainer#setupMessageListener(Object)} method.
   *
   * <p>Use this instead of {@link #createBatchInterceptor()} with Spring Kafka 2.7, which does not
   * notify the batch interceptor when a batch does not complete, for example when the transaction
   * could not be started after the batch was intercepted. Do not use both for the same container,
   * because each of them creates a span for the batch. With Spring Kafka 2.8 and later, prefer
   * {@link #createBatchInterceptor()}. The wrapper preserves the Spring Kafka 2.7 {@code
   * ConsumerSeekAware} callbacks, but not every optional listener interface or later-only callbacks
   * such as {@code onFirstPoll}.
   */
  public <K, V> BatchMessageListener<K, V> createBatchMessageListener(
      BatchMessageListener<K, V> listener) {
    return InstrumentedBatchMessageListener.create(batchProcessInstrumenter, listener);
  }

  /**
   * Returns a consumer wrapper that marks records returned by {@code poll} as owned by Spring
   * Kafka.
   *
   * <p>Use this as a consumer-factory post-processor when the consumer is instrumented with {@code
   * KafkaTelemetry}. It suppresses raw Kafka processing spans for every record from the wrapped
   * consumer, including during Spring's pre-listener batch conversion. Use it only for consumers
   * whose processing is traced by Spring instrumentation, not unrelated consumers using a shared
   * factory.
   */
  @SuppressWarnings("unchecked") // casting Proxy.newProxyInstance result
  public <K, V> Consumer<K, V> instrument(Consumer<K, V> consumer) {
    return (Consumer<K, V>)
        Proxy.newProxyInstance(
            SpringKafkaTelemetry.class.getClassLoader(),
            new Class<?>[] {Consumer.class},
            (proxy, method, args) -> {
              Object result;
              try {
                result = method.invoke(consumer, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
              if ("poll".equals(method.getName()) && result instanceof ConsumerRecords) {
                for (ConsumerRecord<?, ?> record :
                    KafkaConsumerContextUtil.getRecords((ConsumerRecords<?, ?>) result)) {
                  KafkaConsumerContextUtil.setRawProcessingEligibility(record, () -> false);
                }
              }
              return result;
            });
  }
}
