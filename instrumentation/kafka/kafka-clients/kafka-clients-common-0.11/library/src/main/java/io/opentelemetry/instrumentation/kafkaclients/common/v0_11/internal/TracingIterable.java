/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TracingIterable<K, V> implements Iterable<ConsumerRecord<K, V>> {
  private final Iterable<ConsumerRecord<K, V>> delegate;
  protected final Instrumenter<KafkaProcessRequest, Void> instrumenter;
  protected final BooleanSupplier wrappingEnabled;
  protected final KafkaConsumerContext consumerContext;
  protected final BooleanSupplier firstTraversal;

  protected TracingIterable(
      Iterable<ConsumerRecord<K, V>> delegate,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext,
      BooleanSupplier firstTraversal) {
    this.delegate = delegate;
    this.instrumenter = instrumenter;
    this.wrappingEnabled = wrappingEnabled;
    this.consumerContext = consumerContext;
    this.firstTraversal = firstTraversal;
  }

  public static <K, V> Iterable<ConsumerRecord<K, V>> wrap(
      Iterable<ConsumerRecord<K, V>> delegate,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext) {
    if (!wrappingEnabled.getAsBoolean()) {
      return delegate;
    }
    AtomicBoolean traversalClaimed = new AtomicBoolean();
    return wrap(
        delegate,
        instrumenter,
        wrappingEnabled,
        consumerContext,
        () -> traversalClaimed.compareAndSet(false, true));
  }

  public static <K, V> Iterable<ConsumerRecord<K, V>> wrap(
      Iterable<ConsumerRecord<K, V>> delegate,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext,
      BooleanSupplier firstTraversal) {
    return new TracingIterable<>(
        delegate, instrumenter, wrappingEnabled, consumerContext, firstTraversal);
  }

  @Override
  public Iterator<ConsumerRecord<K, V>> iterator() {
    Iterator<ConsumerRecord<K, V>> iterator = delegate.iterator();
    return firstTraversal.getAsBoolean()
        ? TracingIterator.wrap(iterator, instrumenter, wrappingEnabled, consumerContext)
        : iterator;
  }

  @Override
  public void forEach(Consumer<? super ConsumerRecord<K, V>> action) {
    iterator().forEachRemaining(action);
  }

  @Override
  public Spliterator<ConsumerRecord<K, V>> spliterator() {
    Spliterator<ConsumerRecord<K, V>> spliterator = delegate.spliterator();
    return firstTraversal.getAsBoolean() && wrappingEnabled.getAsBoolean()
        ? new TracingSpliterator<>(spliterator, instrumenter, wrappingEnabled, consumerContext)
        : spliterator;
  }
}
