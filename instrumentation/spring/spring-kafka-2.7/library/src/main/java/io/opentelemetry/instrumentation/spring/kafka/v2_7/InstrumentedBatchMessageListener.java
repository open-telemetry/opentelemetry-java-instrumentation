/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.BatchAcknowledgingConsumerAwareMessageListener;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.listener.DelegatingMessageListener;
import org.springframework.kafka.listener.ListenerType;
import org.springframework.kafka.listener.ListenerUtils;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Wraps the batch listener invocation in a process span. Unlike {@link
 * InstrumentedBatchInterceptor}, the span is started and ended around the listener invocation on
 * the consumer thread, so it does not depend on the container calling the batch interceptor success
 * or failure callbacks.
 */
@SuppressWarnings("ThreadLocalUsage") // retry tracking
class InstrumentedBatchMessageListener<K, V>
    implements BatchAcknowledgingConsumerAwareMessageListener<K, V> {

  private static final ThreadLocal<WeakReference<Object>> lastProcessed = new ThreadLocal<>();

  private final Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter;
  private final BatchMessageListener<K, V> delegate;
  private final ListenerType delegateType;

  static <K, V> BatchMessageListener<K, V> create(
      Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter,
      BatchMessageListener<K, V> delegate) {
    if (delegate instanceof ConsumerSeekAware) {
      return new SeekAware<>(batchProcessInstrumenter, delegate);
    }
    return new InstrumentedBatchMessageListener<>(batchProcessInstrumenter, delegate);
  }

  private InstrumentedBatchMessageListener(
      Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter,
      BatchMessageListener<K, V> delegate) {
    this.batchProcessInstrumenter = batchProcessInstrumenter;
    this.delegate = delegate;
    this.delegateType = determineListenerType(delegate);
  }

  // same as the container, which determines the listener type of the innermost delegate
  private static ListenerType determineListenerType(Object listener) {
    Object target = listener;
    while (target instanceof DelegatingMessageListener) {
      target = ((DelegatingMessageListener<?>) target).getDelegate();
    }
    return ListenerUtils.determineListenerType(target);
  }

  @Override
  public void onMessage(List<ConsumerRecord<K, V>> data) {
    onMessage(data, null, null);
  }

  @Override
  public void onMessage(List<ConsumerRecord<K, V>> data, @Nullable Acknowledgment acknowledgment) {
    onMessage(data, acknowledgment, null);
  }

  @Override
  public void onMessage(List<ConsumerRecord<K, V>> data, @Nullable Consumer<?, ?> consumer) {
    onMessage(data, null, consumer);
  }

  @Override
  public void onMessage(
      List<ConsumerRecord<K, V>> data,
      @Nullable Acknowledgment acknowledgment,
      @Nullable Consumer<?, ?> consumer) {
    process(
        data,
        toConsumerRecords(data),
        consumer,
        () -> invokeDelegate(data, acknowledgment, consumer));
  }

  @Override
  public void onMessage(
      ConsumerRecords<K, V> records,
      @Nullable Acknowledgment acknowledgment,
      Consumer<K, V> consumer) {
    process(
        records, records, consumer, () -> delegate.onMessage(records, acknowledgment, consumer));
  }

  @Override
  public boolean wantsPollResult() {
    return delegate.wantsPollResult();
  }

  private void invokeDelegate(
      List<ConsumerRecord<K, V>> data,
      @Nullable Acknowledgment acknowledgment,
      @Nullable Consumer<?, ?> consumer) {
    switch (delegateType) {
      case ACKNOWLEDGING_CONSUMER_AWARE:
        delegate.onMessage(data, acknowledgment, consumer);
        break;
      case ACKNOWLEDGING:
        delegate.onMessage(data, acknowledgment);
        break;
      case CONSUMER_AWARE:
        delegate.onMessage(data, consumer);
        break;
      case SIMPLE:
        delegate.onMessage(data);
        break;
    }
  }

  private void process(
      Object data,
      ConsumerRecords<?, ?> records,
      @Nullable Consumer<?, ?> consumer,
      Runnable invocation) {
    if (isRetry(data)) {
      invocation.run();
      return;
    }
    Context parentContext = getParentContext(records);
    KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumer);
    if (!batchProcessInstrumenter.shouldStart(parentContext, request)) {
      invocation.run();
      return;
    }
    Context context = batchProcessInstrumenter.start(parentContext, request);
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      invocation.run();
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      batchProcessInstrumenter.end(context, request, null, error);
      // the container retries a failed batch by invoking the listener again with the same data
      lastProcessed.set(new WeakReference<>(data));
    }
  }

  private static boolean isRetry(Object data) {
    WeakReference<Object> reference = lastProcessed.get();
    return reference != null && reference.get() == data;
  }

  private static Context getParentContext(ConsumerRecords<?, ?> records) {
    Context receiveContext = KafkaConsumerContextUtil.get(records).getContext();
    if (receiveContext == null) {
      for (ConsumerRecord<?, ?> record : records) {
        receiveContext = KafkaConsumerContextUtil.get(record).getContext();
        break;
      }
    }

    // use the receive CONSUMER span as parent if it's available
    return receiveContext != null ? receiveContext : Context.current();
  }

  private static <K, V> ConsumerRecords<K, V> toConsumerRecords(List<ConsumerRecord<K, V>> data) {
    Map<TopicPartition, List<ConsumerRecord<K, V>>> recordsByPartition = new LinkedHashMap<>();
    for (ConsumerRecord<K, V> record : data) {
      recordsByPartition
          .computeIfAbsent(
              new TopicPartition(record.topic(), record.partition()), tp -> new ArrayList<>())
          .add(record);
    }
    return new ConsumerRecords<>(recordsByPartition);
  }

  private static final class SeekAware<K, V> extends InstrumentedBatchMessageListener<K, V>
      implements ConsumerSeekAware {

    private final ConsumerSeekAware seekAware;

    SeekAware(
        Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter,
        BatchMessageListener<K, V> delegate) {
      super(batchProcessInstrumenter, delegate);
      this.seekAware = (ConsumerSeekAware) delegate;
    }

    @Override
    public void registerSeekCallback(ConsumerSeekCallback callback) {
      seekAware.registerSeekCallback(callback);
    }

    @Override
    public void onPartitionsAssigned(
        Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
      seekAware.onPartitionsAssigned(assignments, callback);
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
      seekAware.onPartitionsRevoked(partitions);
    }

    @Override
    public void onIdleContainer(
        Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
      seekAware.onIdleContainer(assignments, callback);
    }

    @Override
    public void unregisterSeekCallback() {
      seekAware.unregisterSeekCallback();
    }
  }
}
