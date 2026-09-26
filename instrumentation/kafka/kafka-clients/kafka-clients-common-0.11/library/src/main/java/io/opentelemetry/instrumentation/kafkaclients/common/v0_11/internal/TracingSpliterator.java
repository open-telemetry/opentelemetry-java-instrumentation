/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class TracingSpliterator<K, V> implements Spliterator<ConsumerRecord<K, V>> {
  private final Spliterator<ConsumerRecord<K, V>> delegate;
  private final Instrumenter<KafkaProcessRequest, Void> instrumenter;
  private final BooleanSupplier wrappingEnabled;
  private final KafkaConsumerContext consumerContext;
  private final TracingIterator<K, V> iterator;

  TracingSpliterator(
      Spliterator<ConsumerRecord<K, V>> delegate,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext) {
    this.delegate = delegate;
    this.instrumenter = instrumenter;
    this.wrappingEnabled = wrappingEnabled;
    this.consumerContext = consumerContext;
    iterator =
        new TracingIterator<>(
            Spliterators.iterator(delegate), instrumenter, wrappingEnabled, consumerContext);
  }

  @Override
  public boolean tryAdvance(Consumer<? super ConsumerRecord<K, V>> action) {
    requireNonNull(action);
    if (!iterator.hasNext()) {
      return false;
    }
    ConsumerRecord<K, V> record = iterator.next();
    try {
      action.accept(record);
    } catch (Throwable t) {
      iterator.closeScopeAndEndSpan(t);
      throw t;
    }
    iterator.closeScopeAndEndSpan();
    return true;
  }

  @Override
  public void forEachRemaining(Consumer<? super ConsumerRecord<K, V>> action) {
    while (tryAdvance(action)) {}
  }

  @Nullable
  @Override
  public Spliterator<ConsumerRecord<K, V>> trySplit() {
    Spliterator<ConsumerRecord<K, V>> split = delegate.trySplit();
    return split == null
        ? null
        : new TracingSpliterator<>(split, instrumenter, wrappingEnabled, consumerContext);
  }

  @Override
  public long estimateSize() {
    return delegate.estimateSize();
  }

  @Override
  public int characteristics() {
    return delegate.characteristics();
  }

  @Override
  public Comparator<? super ConsumerRecord<K, V>> getComparator() {
    return delegate.getComparator();
  }
}
